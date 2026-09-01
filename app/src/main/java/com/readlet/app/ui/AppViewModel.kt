package com.readlet.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.readlet.app.ShareFeedback
import androidx.lifecycle.viewModelScope
import com.readlet.app.ReadletApp
import com.readlet.app.data.Settings
import com.readlet.app.data.db.Card
import com.readlet.app.data.db.CardWord
import com.readlet.app.data.db.WordFreq
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 三个 Tab 的目标页（收件箱已并入卡片库） */
enum class Tab(val label: String, val icon: String) {
    Library("卡片库", "🗂"),
    Review("复习", "🎴"),
    Stats("统计", "📊"),
}

data class InboxItem(
    val card: Card,
    val words: List<CardWord>,
) {
    val keywords: String get() = words.joinToString(" · ") { it.word }
}

data class StatsData(
    val totalCards: Int = 0,
    val analyzedCards: Int = 0,
    val distinctWords: Int = 0,
    val wordFreq: List<WordFreq> = emptyList(),
)

data class ReviewSession(
    val card: Card? = null,
    val isPractice: Boolean = false,
    val remaining: Int = 0,
    val dueCount: Int = 0,
    val doneToday: Int = 0,
    /** 队列是否已加载完成（首帧未加载完时避免闪现「今日复习完成」空态导致字体跳变）。 */
    val ready: Boolean = false,
)

data class SettingsState(
    val apiKey: String,
    val baseUrl: String,
    val model: String,
    val glossary: String,
)

/** Toast 消息；undo=true 时 snackbar 附带「撤销」操作。 */
data class ToastMsg(val text: String, val undo: Boolean = false)

/** 一键分析批量进度；非 null 期间按钮显示「分析中 done/total」且不可点击。 */
data class AnalyzeProgress(val total: Int, val done: Int)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as ReadletApp).repository
    private val settings = (app as ReadletApp).settings

    // ---------- 页面状态 ----------
    private val _tab = MutableStateFlow(Tab.Library)
    val tab: StateFlow<Tab> = _tab

    private val _analyzing = MutableStateFlow<AnalyzeProgress?>(null)
    val analyzing: StateFlow<AnalyzeProgress?> = _analyzing

    private val analysisTick = MutableStateFlow(0)

    private val _inboxItems = MutableStateFlow<List<InboxItem>>(emptyList())
    val inboxItems: StateFlow<List<InboxItem>> = _inboxItems

    val totalCards: StateFlow<Int> = repo.observeTotalCount()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val analyzedCount: StateFlow<Int> = repo.observeAnalyzedCount()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** 每日复习量（epochDay → 次数），统计页数据源。 */
    val dailyCounts: StateFlow<Map<Long, Int>> = repo.observeDailyCounts()
        .map { list -> list.associate { it.reviewedDay to it.cnt } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** 大模型累计调用次数 / token 用量（统计页）。 */
    val llmCallCount: StateFlow<Int> = repo.observeLlmCallCount()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val llmTotalTokens: StateFlow<Int> = repo.observeLlmTotalTokens()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val streak: StateFlow<Int> = dailyCounts.map { counts ->
        var day = LocalDate.now().toEpochDay()
        // 今天还没复习不算断签：从昨天起算（多邻国式打卡语义）。
        if (!counts.containsKey(day)) day--
        var s = 0
        while (counts.containsKey(day)) { s++; day-- }
        s
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // ---------- 复习 ----------
    private val dueQueue = MutableStateFlow<List<Card>>(emptyList())
    private val practiceQueue = MutableStateFlow<List<Card>>(emptyList())
    private val _practiceWord = MutableStateFlow<String?>(null)
    val practiceWord: StateFlow<String?> = _practiceWord

    val reviewSession: StateFlow<ReviewSession> = combine(
        dueQueue, practiceQueue, dailyCounts,
    ) { due, practice, days ->
        val today = LocalDate.now().toEpochDay()
        val doneToday = days[today] ?: 0
        val p = practice.firstOrNull()
        if (p != null) {
            ReviewSession(
                card = p, isPractice = true, remaining = practice.size,
                dueCount = due.size, doneToday = doneToday,
                ready = true,
            )
        } else {
            val d = due.firstOrNull()
            ReviewSession(
                card = d, isPractice = false, remaining = due.size,
                dueCount = due.size, doneToday = doneToday,
                ready = true,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ReviewSession())

    /** 当前复习卡的重点词（随 session.card 预载）：首帧即带高亮渲染，避免异步加载导致的字体/样式跳变。
     * 以整张卡为键（而非仅 id）：重新分析后队列刷新出同 id 新内容时也要重查词表。 */
    val reviewWords: StateFlow<List<CardWord>> = reviewSession
        .map { it.card }
        .distinctUntilChanged()
        .map { card -> if (card != null) repo.wordsOfCard(card.id) else emptyList() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 每卡排程复习次数（type=0）：卡片库/复习页「已复习 N 次」数据源。 */
    val reviewCounts: StateFlow<Map<Long, Int>> = repo.observeReviewCounts()
        .map { list -> list.associate { it.cardId to it.cnt } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // ---------- 统计 ----------
    private val statsTick = MutableStateFlow(0)
    private val _stats = MutableStateFlow(StatsData())
    val stats: StateFlow<StatsData> = _stats

    // ---------- 覆盖页 ----------
    val detailCardId = MutableStateFlow<Long?>(null)
    val detailWord = MutableStateFlow<String?>(null)
    val settingsOpen = MutableStateFlow(false)

    /** 正在被手动重新分析的卡片 id：防重复点击（同一张卡分析中则忽略后续点击）。 */
    private val _detailAnalyzing = MutableStateFlow<Set<Long>>(emptySet())
    val detailAnalyzing: StateFlow<Set<Long>> = _detailAnalyzing

    /** 删除待撤销：删除时暂存卡片与词表，snackbar「撤销」时恢复。 */
    private var pendingRestore: Pair<Card, List<CardWord>>? = null

    private val _toast = MutableStateFlow<ToastMsg?>(null)
    val toast: StateFlow<ToastMsg?> = _toast

    private val _settingsState = MutableStateFlow(
        SettingsState(settings.apiKey, settings.baseUrl, settings.model, settings.glossary)
    )
    val settingsState: StateFlow<SettingsState> = _settingsState

    init {
        viewModelScope.launch {
            combine(repo.observeCards(), analysisTick) { cards, _ -> cards }
                .collect { cards ->
                    val words = repo.wordsOfCards(cards.map { it.id })
                    val byCard = words.groupBy { it.cardId }
                    _inboxItems.value = cards.map { c -> InboxItem(c, byCard[c.id] ?: emptyList()) }
                }
        }
        viewModelScope.launch {
            combine(repo.observeTotalCount(), repo.observeAnalyzedCount(), statsTick) { t, a, _ -> t to a }
                .collect { (t, a) ->
                    _stats.value = StatsData(
                        totalCards = t,
                        analyzedCards = a,
                        distinctWords = repo.distinctWordCount(),
                        wordFreq = repo.wordFreq(),
                    )
                }
        }
    }

    // ---------- 动作 ----------

    fun selectTab(t: Tab) {
        _tab.value = t
        if (t == Tab.Review) refreshDue()
    }

    /**
     * 分享接收入口（onCreate / onNewIntent）：仅入库为待分析，由用户手动分析。相同句子去重。
     * 外部分享后 app 立即退回后台，Compose snackbar 不可见 → 用系统 Toast 悬浮于来源 app 之上提示，
     * 让用户知道已成功、无需反复点分享。onDone 在入库完成后回调（幽灵接收页用它 finish 自身）。
     */
    fun onShared(text: String, source: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val (_, added) = repo.acceptShared(text, source)
                analysisTick.value++
                statsTick.value++
                ShareFeedback.notify(getApplication(), added)
            } finally {
                onDone()
            }
        }
    }

    /** App 启动：词表缺口回填 + 刷新队列（不再自动补分析，分析全部手动触发）。 */
    fun onAppStart() {
        viewModelScope.launch {
            repo.backfillWordGaps()
            analysisTick.value++
            refreshDue()
        }
    }

    /** 一键分析：手动补分析所有 待分析/失败/卡死 的卡片。批量期间按钮显示「分析中 done/total」并禁止重复触发。 */
    fun analyzeAll() {
        if (_analyzing.value != null) return
        viewModelScope.launch {
            val count = repo.pendingCount()
            if (count == 0) {
                showToast("没有待分析的卡片")
                return@launch
            }
            _analyzing.value = AnalyzeProgress(count, 0)
            try {
                repo.analyzePending { done -> _analyzing.value = AnalyzeProgress(count, done) }
                analysisTick.value++
                statsTick.value++
                showToast("已完成分析 $count 张卡片")
            } finally {
                _analyzing.value = null
            }
        }
    }

    fun refreshDue() {
        viewModelScope.launch {
            dueQueue.value = repo.dueCards()
        }
    }

    fun openDetail(cardId: Long) {
        detailCardId.value = cardId
    }

    fun closeDetail() {
        detailCardId.value = null
    }

    fun openWord(word: String) {
        detailWord.value = word
    }

    fun closeWord() {
        detailWord.value = null
        _practiceWord.value = null
        practiceQueue.value = emptyList()
    }

    /** 词频 → 加练：载入包含该词的所有句子。 */
    fun startPractice(word: String) {
        viewModelScope.launch {
            _practiceWord.value = word
            practiceQueue.value = repo.cardsByWord(word)
            _tab.value = Tab.Review
        }
    }

    fun grade(quality: Int) {
        val session = reviewSession.value
        val card = session.card ?: return
        viewModelScope.launch {
            if (session.isPractice) {
                repo.gradePractice(card.id, quality)
                practiceQueue.value = practiceQueue.value.filterNot { it.id == card.id }
                if (practiceQueue.value.isEmpty()) {
                    _practiceWord.value = null
                    refreshDue()
                }
            } else {
                repo.gradeScheduled(card.id, quality)
                dueQueue.value = dueQueue.value.filterNot { it.id == card.id }
                if (dueQueue.value.isEmpty()) refreshDue()
            }
        }
    }

    /** 详情页手动重新分析。防重复点击：同一张卡分析中则忽略。 */
    fun reanalyze(cardId: Long) {
        if (_detailAnalyzing.value.contains(cardId)) return
        viewModelScope.launch {
            _detailAnalyzing.value = _detailAnalyzing.value + cardId
            val started = try {
                repo.reanalyze(cardId)
            } finally {
                _detailAnalyzing.value = _detailAnalyzing.value - cardId
            }
            if (started) {
                // 重新分析改了卡片内容：复习队列里还是旧快照，就地刷新（含加练队列）。
                refreshDue()
                _practiceWord.value?.let { w ->
                    if (practiceQueue.value.any { it.id == cardId }) {
                        practiceQueue.value = repo.cardsByWord(w)
                    }
                }
            }
            analysisTick.value++
            statsTick.value++
            showToast(if (started) "已重新分析" else "该卡片正在分析中")
        }
    }

    /** 开始学习：待学习卡进入复习排程（次日到期），卡片库角标即时更新。
     * 卡片库「待学习」流程自动连播：加入复习后直接切到下一张待学习卡，全部学完才关闭详情。
     * （词频加练/复习页里打开的详情不连播，保持原行为返回原处。） */
    fun learnCard(cardId: Long) {
        viewModelScope.launch {
            repo.learnCard(cardId)
            analysisTick.value++
            if (tab.value == Tab.Library) {
                val next = repo.nextUnlearned(cardId)
                if (next != null) {
                    detailCardId.value = next.id
                } else {
                    closeDetail()
                    showToast("已加入复习，明天起进入复习排程；待学习卡片已全部学完 🎉")
                }
            } else {
                showToast("已加入复习，明天起进入复习排程")
            }
        }
    }

    /** 标记已掌握：该句不再进入复习队列。 */
    fun masterCard(cardId: Long) {
        viewModelScope.launch {
            repo.masterCard(cardId)
            dueQueue.value = dueQueue.value.filterNot { it.id == cardId }
            practiceQueue.value = practiceQueue.value.filterNot { it.id == cardId }
            analysisTick.value++
            showToast("已标记掌握，不再进入复习")
        }
    }

    /** 取消已掌握：恢复进入复习队列。 */
    fun unmasterCard(cardId: Long) {
        viewModelScope.launch {
            repo.unmasterCard(cardId)
            refreshDue()
            analysisTick.value++
            showToast("已取消掌握，重新进入复习")
        }
    }

    fun saveSettings(apiKey: String, baseUrl: String, model: String, glossary: String) {
        settings.apiKey = apiKey.trim()
        settings.baseUrl = baseUrl.trim().ifBlank { Settings.DEFAULT_BASE_URL }
        settings.model = model.trim().ifBlank { Settings.DEFAULT_MODEL }
        settings.glossary = glossary.trim()
        repo.refreshClient()
        _settingsState.value = SettingsState(settings.apiKey, settings.baseUrl, settings.model, settings.glossary)
        settingsOpen.value = false
        showToast("设置已保存")
    }

    /** 导出备份：全部卡片/词表/复习记录 + 设置 → 用户选择的 zip。 */
    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                repo.exportBackup(uri)
                showToast("备份已导出")
            } catch (e: Exception) {
                showToast("导出失败：${e.message ?: "未知错误"}")
            }
        }
    }

    /** 导入备份：整体替换全部数据与设置，成功后刷新所有页面状态。 */
    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                repo.importBackup(uri)
                repo.refreshClient()
                _settingsState.value = SettingsState(settings.apiKey, settings.baseUrl, settings.model, settings.glossary)
                analysisTick.value++
                statsTick.value++
                refreshDue()
                settingsOpen.value = false
                showToast("备份导入成功")
            } catch (e: Exception) {
                showToast("导入失败：${e.message ?: "未知错误"}")
            }
        }
    }

    fun showToast(msg: String) {
        _toast.value = ToastMsg(msg)
    }

    /** 左滑删除卡片（词表级联删除），snackbar 可撤销。 */
    fun deleteCard(cardId: Long) {
        viewModelScope.launch {
            val card = repo.cardById(cardId) ?: return@launch
            pendingRestore = card to repo.wordsOfCard(cardId)
            repo.deleteCard(cardId)
            analysisTick.value++
            statsTick.value++
            _toast.value = ToastMsg("已删除", undo = true)
        }
    }

    /** 撤销删除：恢复卡片与词表（新 id）。 */
    fun undoDelete() {
        val restore = pendingRestore ?: return
        pendingRestore = null
        viewModelScope.launch {
            repo.restoreCard(restore.first, restore.second)
            analysisTick.value++
            statsTick.value++
            _toast.value = ToastMsg("已恢复")
        }
    }

    fun dismissToast() {
        _toast.value = null
    }

    // ---------- 数据加载（覆盖页） ----------

    /** 单卡实时观察（详情页）：状态变化即时反映，不再是一次性快照。 */
    fun observeCard(cardId: Long): kotlinx.coroutines.flow.Flow<Card?> = repo.observeCard(cardId)

    /** 词的原形（词表变形还原，内存查表）；原形/未命中返回 null。详情页/复习页音标行展示。 */
    fun lemmaOf(word: String): String? = repo.lemmaOf(word)

    suspend fun loadCard(cardId: Long): Card? = repo.cardById(cardId)
    suspend fun loadWords(cardId: Long): List<CardWord> = repo.wordsOfCard(cardId)
    suspend fun loadCardsByWord(word: String): List<Card> = repo.cardsByWord(word)
    suspend fun loadWordFreq(): List<WordFreq> = repo.wordFreq()

    fun decodePairs(json: String?): List<Pair<String, String>> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val row = arr.optJSONArray(i) ?: return@mapNotNull null
                if (row.length() < 2) null else row.getString(0) to row.getString(1)
            }
        } catch (_: Exception) { emptyList() }
    }

    fun decodeCollocations(json: String?): List<List<String>> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val row = arr.optJSONArray(i) ?: return@mapNotNull null
                (0 until row.length()).map { j -> row.getString(j) }
            }
        } catch (_: Exception) { emptyList() }
    }

    // ---------- 统计派生（纯函数） ----------

    /** 近 N 天每日复习量（升序）。 */
    fun recentCounts(counts: Map<Long, Int>, days: Int): List<Pair<Long, Int>> {
        val today = LocalDate.now().toEpochDay()
        return (days - 1 downTo 0).map { offset ->
            val d = today - offset
            d to (counts[d] ?: 0)
        }
    }

    /** 近 12 周热力图：每列一周（周一起），行=周一~周日。 */
    fun heatmapWeeks(counts: Map<Long, Int>): List<List<Int>> {
        val monday = LocalDate.now().with(DayOfWeek.MONDAY).toEpochDay()
        return (11 downTo 0).map { w ->
            val weekStart = monday - w * 7
            (0L..6L).map { d -> counts[weekStart + d] ?: 0 }
        }
    }

    /** 热力图月份轴：月份起始列 → "M月" 标签（列 0 = 最旧一周，列 11 = 本周）。 */
    fun heatmapMonths(): List<Pair<Int, String>> {
        val monday = LocalDate.now().with(DayOfWeek.MONDAY)
        val labels = mutableListOf<Pair<Int, String>>()
        var prevMonth = -1
        for (w in 11 downTo 0) {
            val ws = monday.minusWeeks(w.toLong())
            if (ws.monthValue != prevMonth) {
                labels += (11 - w) to "${ws.monthValue}月"
                prevMonth = ws.monthValue
            }
        }
        return labels
    }

    /** 今天在热力图里的 (列, 行)：列 0..11，行 0=周一..6=周日。 */
    fun heatmapToday(): Pair<Int, Int> {
        val today = LocalDate.now()
        val monday = today.with(DayOfWeek.MONDAY)
        val col = 11 - (ChronoUnit.DAYS.between(monday, today) / 7).toInt()
        return col to (today.dayOfWeek.value - 1)
    }

    fun monthStats(counts: Map<Long, Int>): Pair<Int, Int> {
        val now = LocalDate.now()
        val monthStart = now.withDayOfMonth(1).toEpochDay()
        val today = now.toEpochDay()
        val learnedDays = counts.count { it.key in monthStart..today }
        val elapsed = (today - monthStart + 1).toInt()
        return learnedDays to (if (elapsed > 0) (learnedDays * 100 / elapsed) else 0)
    }

    fun formatDay(epochDay: Long): String {
        val d = LocalDate.ofEpochDay(epochDay)
        return "${d.monthValue}/${d.dayOfMonth}"
    }
}

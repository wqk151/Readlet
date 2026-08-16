package com.readlet.app.data.repo

import android.content.Context
import androidx.room.withTransaction
import com.readlet.app.data.Settings
import com.readlet.app.data.WordLevels
import com.readlet.app.data.db.AppDatabase
import com.readlet.app.data.db.Card
import com.readlet.app.data.db.CardStatus
import com.readlet.app.data.db.CardWord
import com.readlet.app.data.db.DailyCount
import com.readlet.app.data.db.LlmUsage
import com.readlet.app.data.db.ReviewLog
import com.readlet.app.data.db.ReviewType
import com.readlet.app.data.db.WordFreq
import com.readlet.app.data.srs.Sm2
import com.readlet.app.llm.AnalyzePrompt
import com.readlet.app.llm.AnalysisParser
import com.readlet.app.llm.AnalyzeResult
import com.readlet.app.llm.LlmClient
import com.readlet.app.llm.LlmException
import com.readlet.app.ui.Keywords
import kotlinx.coroutines.delay
import java.time.LocalDate

/** 句中分词（字母+撇号），级别词补缺用。 */
private val TOKEN = Regex("[A-Za-z']+")

/**
 * 核心业务编排：采集 → 分析（LLM+容错链）→ 复习（SM-2）→ 统计。
 * 线程安全：内部状态仅 [llmClient]，由调用方（ViewModel scope）串行使用。
 */
class CardRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: Settings,
    private val wordLevels: WordLevels,
) {
    private val cardDao = db.cardDao()
    private val wordDao = db.cardWordDao()
    private val logDao = db.reviewLogDao()
    private val llmDao = db.llmUsageDao()

    var llmClient: LlmClient = buildClient()
        private set

    /** 分析中的卡片 id 集合：防止并发/重复分析同一张卡（跨协程竞态）。 */
    private val inFlight = mutableSetOf<Long>()

    private fun buildClient() = LlmClient(
        baseUrl = settings.baseUrl,
        apiKey = settings.apiKey,
        model = settings.model,
    )

    /** 设置变更后重建客户端。 */
    fun refreshClient() {
        llmClient = buildClient()
    }

    // ---------- 采集 ----------

    /**
     * 分享进来：入库 + 异步分析。
     * @return (卡片 id, 是否新建) — 相同文本已收录过则直接复用，不重复建卡/分析。
     */
    suspend fun acceptShared(text: String, source: String): Pair<Long, Boolean> {
        val cleaned = cleanSharedText(text)
        findIdByText(cleaned)?.let { existing ->
            return existing to false
        }
        val id = cardDao.insert(
            Card(text = cleaned, source = source.ifBlank { "系统分享" }, status = CardStatus.PENDING)
        )
        analyzeCard(id)
        return id to true
    }

    /** 清洗分享文本：行内空白压成单空格、去掉行尾空白、连续空行压成一行；**保留段落**（换行不删除）。 */
    private fun cleanSharedText(text: String): String =
        text.trim()
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("(?m)[ \\t]+$"), "")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

    /** 扁平化（换行压成单空格）：去重比对用。 */
    private fun flatten(text: String): String = text.trim().replace(Regex("\\s+"), " ")

    /** 精确匹配（新数据含换行）→ 扁平化比对（兼容旧数据被压成一段的卡片），避免重复建卡。 */
    private suspend fun findIdByText(text: String): Long? {
        cardDao.findIdByText(text)?.let { return it }
        val flat = flatten(text)
        return cardDao.allIdTexts().firstOrNull { (_, t) -> flatten(t).equals(flat, ignoreCase = true) }?.id
    }

    /** 启动时补分析所有 待分析/失败 的卡片。 */
    suspend fun retryPending() {
        cardDao.pendingCards().forEach { analyzeCard(it.id) }
    }

    /**
     * 词表升级后回填旧卡词条缺口（音标/级别）：老数据分析时词表尚未覆盖（如 loom 无音标），
     * 启动时对空缺词条查表补齐，幂等（无缺口时零更新）。四级基础词按原规则不补级别。
     */
    suspend fun backfillWordGaps() {
        val rows = wordDao.withMissingMeta()
        if (rows.isEmpty()) return
        val updates = ArrayList<CardWord>()
        for (cw in rows) {
            val entry = wordLevels.lookup(cw.word) ?: continue
            var changed = false
            val phonetic = cw.phonetic?.takeIf { it.isNotBlank() }
                ?: entry.phonetic.takeIf { it.isNotBlank() }?.also { changed = true }
            val level = cw.level?.takeIf { it.isNotEmpty() }
                ?: entry.level.takeIf { it.isNotEmpty() && !entry.base }?.also { changed = true }
            if (changed) updates.add(cw.copy(phonetic = phonetic, level = level))
        }
        if (updates.isNotEmpty()) wordDao.updateAll(updates)
    }

    /** 待分析/失败/分析中的卡片数（一键分析按钮显示用）。 */
    suspend fun pendingCount(): Int = cardDao.pendingCards().size

    /** 重新分析（详情页手动触发）。返回是否真正启动（false = 已有分析任务在跑，点击被拦截）。 */
    suspend fun reanalyze(cardId: Long): Boolean = analyzeCard(cardId)

    // ---------- 分析 ----------

    /**
     * 容错链：ANALYZING → (LLM 重试 1 次) → 解析校验 → 事务写库；
     * 网络类失败 → PENDING（等 retryPending）；解析/服务端类失败 → FAILED（可手动重试）。
     * inFlight 拦截：同一张卡同时只会有一个分析任务。
     * @return 是否真正启动（false = 已有一个分析任务在跑）。
     */
    private suspend fun analyzeCard(cardId: Long): Boolean {
        if (!inFlight.add(cardId)) return false // 已在分析中，跳过（防止并发重复分析）
        try {
            analyzeCardInner(cardId)
        } finally {
            inFlight.remove(cardId)
        }
        return true
    }

    private suspend fun analyzeCardInner(cardId: Long) {
        val card = cardDao.byId(cardId) ?: return
        cardDao.update(card.copy(status = CardStatus.ANALYZING))

        var lastError: Exception? = null
        repeat(2) { attempt ->
            try {
                val resp = llmClient.analyze(AnalyzePrompt.system(context, settings.glossary), card.text)
                // 调用已产生费用，无论后续解析成败都记账（解析失败也已消耗 token）。
                llmDao.insert(
                    LlmUsage(
                        promptTokens = resp.promptTokens,
                        completionTokens = resp.completionTokens,
                        totalTokens = resp.promptTokens + resp.completionTokens,
                    )
                )
                val result = AnalysisParser.parse(resp.content)
                if (result.keywords.isEmpty() && result.mode != "word" && result.mode != "phrase") {
                    throw LlmException.BadResponse("解析结果缺少 keywords")
                }
                val words = buildCardWords(card, result)
                db.withTransaction {
                    cardDao.update(
                        card.copy(
                            status = CardStatus.ANALYZED,
                            translation = result.translation,
                            pointsJson = encode(result.points.map { listOf(it.expr, it.meaning) }),
                            grammarJson = encode(result.grammar.map { listOf(it.structure, it.explanation) }),
                            collocationsJson = encode(result.collocations.map {
                                listOf(it.phrase, it.meaning, it.example.orEmpty(), it.exampleCn.orEmpty())
                            }),
                            analyzedAt = System.currentTimeMillis(),
                        )
                    )
                    // 重新分析时旧词标注一并重建，防止累积重复。
                    wordDao.deleteByCard(cardId)
                    if (words.isNotEmpty()) wordDao.insertAll(words)
                }
                return
            } catch (e: LlmException) {
                lastError = e
                if (attempt == 0) delay(1500)
            } catch (e: Exception) {
                lastError = e
                if (attempt == 0) delay(1000)
            }
        }
        val nextStatus = if (lastError is LlmException.Network) CardStatus.PENDING else CardStatus.FAILED
        cardDao.update(card.copy(status = nextStatus))
    }

    /**
     * 组装 CardWord 列表：
     * 1. LLM 关键词按 [Keywords.splitKeyword] 拆分（LLM 偶发把并列结构合并成一个关键词），
     *    每项一条；命中级别表则补 level/phonetic/释义兜底。
     * 2. 句子分词 → 级别表查缺补漏（六级/考研/雅思/专四/专八），跳过已被 LLM 关键词覆盖的词，
     *    上限 5 个（防止整句刷角标）。
     */
    private fun buildCardWords(card: Card, result: AnalyzeResult): List<CardWord> {
        val words = ArrayList<CardWord>()
        val seen = HashSet<String>()
        var idx = 0
        for (k in result.keywords) {
            for (part in Keywords.splitKeyword(k.word)) {
                if (!seen.add(part.lowercase())) continue
                val entry = wordLevels.lookup(part)
                // 过滤简单词：词表确认的四级基础词（fog/racket/wake 这类）不作为重点词；
                // 词表未收录的词保留（多为词组/语境表达，无法判定难度，删了卡片就没词可学）。
                if (entry != null && entry.base) continue
                val rawMeaning = k.meaningInContext?.takeIf { it.isNotBlank() }
                    ?: entry?.meaning?.takeIf { it.isNotBlank() }
                // LLM 偶发不返回词性：词表释义带词性前缀时拆出补位（如 canyon → n.），保证词性位置一致。
                val (posFromMeaning, meaning) = if (k.pos.isNullOrBlank()) {
                    Keywords.splitPos(rawMeaning ?: "")
                } else {
                    null to rawMeaning
                }
                words.add(
                    CardWord(
                        cardId = card.id,
                        word = part,
                        // 词表兜底同样要过 blank 守卫：TSV 中音标列可能为空串，
                        // 空串非 null 会让 UI 渲染空行（loomed 与级别标记之间的空行）。
                        phonetic = k.phonetic?.takeIf { it.isNotBlank() }
                            ?: entry?.phonetic?.takeIf { it.isNotBlank() },
                        pos = if (!k.pos.isNullOrBlank()) k.pos else posFromMeaning,
                        meaningInContext = meaning,
                        orderIdx = idx++,
                        level = entry?.level?.takeIf { it.isNotEmpty() },
                    )
                )
            }
        }
        // 级别词补缺：仅完整句子卡（单词/短语卡的输入本身就是关键词）；上限 5 个。
        // 跳过功能词与四级基础词（词表会误标 were/even/air 这类词，补了只会刷满高亮）。
        if (result.mode == "sentence" && words.size < 10) {
            val covered = words.map { it.word.lowercase() }
            val processed = HashSet<String>()
            var added = 0
            for (t in TOKEN.findAll(card.text)) {
                val tl = t.value.lowercase()
                if (!processed.add(tl)) continue
                if (Keywords.isFunctionWord(tl)) continue
                // 首字母大写多为专有名词（Harry/McGonagall），柯林斯会误标级别，跳过
                if (t.value[0].isUpperCase()) continue
                if (covered.any { it == tl || it.contains(tl) || tl.contains(it) }) continue
                val entry = wordLevels.lookup(tl) ?: continue
                // 词表扩容后含无级别词（仅音标/释义兜底），补缺只加有级别的考试词。
                if (entry.base || entry.level.isEmpty()) continue
                // 词表释义自带词性前缀（「n. 峡谷」），拆出 pos 独立展示，避免「n.」出现在翻译前。
                val (pos, meaning) = Keywords.splitPos(entry.meaning)
                words.add(
                    CardWord(
                        cardId = card.id,
                        word = t.value,
                        phonetic = entry.phonetic.takeIf { it.isNotBlank() },
                        pos = pos,
                        meaningInContext = meaning,
                        orderIdx = idx++,
                        level = entry.level,
                    )
                )
                added++
                if (added >= 5) break
            }
        }
        return words
    }

    private fun encode(list: List<List<String>>): String? {
        if (list.isEmpty()) return null
        val arr = org.json.JSONArray()
        list.forEach { arr.put(org.json.JSONArray(it)) }
        return arr.toString()
    }

    // ---------- 复习 ----------

    /** 每日队列 = 到期卡片（dueAt≤今天）；只含点过「开始学习」的卡，待学习卡不进队列。 */
    suspend fun dueCards(): List<Card> = cardDao.dueQueue(LocalDate.now().toEpochDay())

    /** 排程评级：更新 SRS 字段 + 写日志。 */
    suspend fun gradeScheduled(cardId: Long, quality: Int) {
        val card = cardDao.byId(cardId) ?: return
        val newCard = Sm2.apply(card, quality)
        cardDao.update(newCard)
        logDao.insert(
            ReviewLog(
                cardId = cardId,
                grade = Sm2.qualityOf(quality),
                type = ReviewType.SCHEDULED,
                reviewedDay = LocalDate.now().toEpochDay(),
                intervalAfter = newCard.interval,
            )
        )
    }

    /** 加练评级：只写日志，不碰 SRS 字段。 */
    suspend fun gradePractice(cardId: Long, quality: Int) {
        val card = cardDao.byId(cardId) ?: return
        logDao.insert(
            ReviewLog(
                cardId = cardId,
                grade = Sm2.qualityOf(quality),
                type = ReviewType.PRACTICE,
                reviewedDay = LocalDate.now().toEpochDay(),
                intervalAfter = card.interval,
            )
        )
    }

    /** 开始学习（方案 A）：待学习卡进入复习排程，次日到期。 */
    suspend fun learnCard(cardId: Long) {
        val card = cardDao.byId(cardId) ?: return
        if (card.dueAt == null && !card.mastered) {
            cardDao.update(card.copy(dueAt = LocalDate.now().plusDays(1).toEpochDay()))
        }
    }

    /** 标记已掌握：退出复习队列（dueAt 置空 + mastered 标记）。 */
    suspend fun masterCard(cardId: Long) {
        val card = cardDao.byId(cardId) ?: return
        cardDao.update(card.copy(mastered = true, dueAt = null))
    }

    /** 取消已掌握：恢复进入复习（当天即到期）。 */
    suspend fun unmasterCard(cardId: Long) {
        val card = cardDao.byId(cardId) ?: return
        cardDao.update(card.copy(mastered = false, dueAt = LocalDate.now().toEpochDay()))
    }

    /** 删除卡片（词表/复习日志级联删除）。 */
    suspend fun deleteCard(cardId: Long) {
        cardDao.byId(cardId)?.let { cardDao.delete(it) }
    }

    /** 撤销删除：原样恢复卡片与词表（分配新 id）。 */
    suspend fun restoreCard(card: Card, words: List<CardWord>) {
        val id = cardDao.insert(card.copy(id = 0))
        if (words.isNotEmpty()) {
            wordDao.insertAll(words.map { it.copy(id = 0, cardId = id) })
        }
    }

    // ---------- 查询 ----------

    suspend fun cardById(id: Long): Card? = cardDao.byId(id)
    suspend fun wordsOfCard(cardId: Long): List<CardWord> = wordDao.byCard(cardId)
    suspend fun wordsOfCards(ids: List<Long>): List<CardWord> =
        if (ids.isEmpty()) emptyList() else wordDao.byCards(ids)

    /** 词的原形（词表变形还原，内存查表）；原形/未命中返回 null。 */
    fun lemmaOf(word: String): String? = wordLevels.lemma(word)

    fun observeCards(): kotlinx.coroutines.flow.Flow<List<Card>> = cardDao.observeAll()
    fun observeCard(id: Long): kotlinx.coroutines.flow.Flow<Card?> = cardDao.observeById(id)
    fun observeDailyCounts(): kotlinx.coroutines.flow.Flow<List<DailyCount>> = logDao.observeDailyCounts()
    fun observeTotalCount(): kotlinx.coroutines.flow.Flow<Int> = cardDao.observeTotalCount()
    fun observeAnalyzedCount(): kotlinx.coroutines.flow.Flow<Int> = cardDao.observeAnalyzedCount()
    fun observePracticeCount(): kotlinx.coroutines.flow.Flow<Int> = logDao.observeCountByType(ReviewType.PRACTICE)
    fun observeLlmCallCount(): kotlinx.coroutines.flow.Flow<Int> = llmDao.observeCallCount()
    fun observeLlmTotalTokens(): kotlinx.coroutines.flow.Flow<Int> = llmDao.observeTotalTokens()

    /**
     * 词频榜：全量拉取后按分隔符拆分重聚合。
     * LLM 偶发把并列结构合并成一个关键词（如 `bottle fame, brew glory, stopper death`），
     * SQL GROUP BY 无法拆开；Kotlin 侧拆完后每项独立成词，点击必能在句中高亮。
     * 数据量为个人级（数百卡），全量拉取无压力。
     */
    suspend fun wordFreq(): List<WordFreq> = aggregateWords().values
        .sortedWith(compareByDescending<WordFreq> { it.freq }.thenBy { it.word })

    /** 去重词/词组数（拆分后）。 */
    suspend fun distinctWordCount(): Int = aggregateWords().size

    /** 包含指定词（拆分后任一分项等于该词，忽略大小写）的所有卡片，按收藏时间倒序。 */
    suspend fun cardsByWord(word: String): List<Card> {
        val w = word.lowercase()
        val ids = LinkedHashSet<Long>()
        for (cw in wordDao.allWords()) {
            if (Keywords.splitKeyword(cw.word).any { it.lowercase() == w }) ids.add(cw.cardId)
        }
        if (ids.isEmpty()) return emptyList()
        return cardDao.byIds(ids.toList()).sortedByDescending { it.createdAt }
    }

    private data class FreqAgg(var freq: Int, val cards: MutableSet<Long>, val display: String)

    private suspend fun aggregateWords(): Map<String, WordFreq> {
        val agg = HashMap<String, FreqAgg>()
        for (cw in wordDao.allWords()) {
            for (part in Keywords.splitKeyword(cw.word)) {
                val key = part.lowercase()
                val a = agg.getOrPut(key) { FreqAgg(0, HashSet(), part) }
                a.freq++
                a.cards.add(cw.cardId)
            }
        }
        return agg.mapValues { (_, a) -> WordFreq(a.display, a.freq, a.cards.size) }
    }
}

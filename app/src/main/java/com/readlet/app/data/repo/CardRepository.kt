package com.readlet.app.data.repo

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Xml
import androidx.room.withTransaction
import com.readlet.app.data.Settings
import com.readlet.app.data.WordLevels
import com.readlet.app.data.db.AppDatabase
import com.readlet.app.data.db.Card
import com.readlet.app.data.db.CardReviewCount
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** 句中分词（字母+撇号），级别词补缺用。 */
private val TOKEN = Regex("[A-Za-z']+")

/** 「一键分析」并发上限。 */
private const val ANALYZE_CONCURRENCY = 5

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

    /** 分析中的卡片 id 集合：防止并发/重复分析同一张卡（跨协程竞态）。并发化后跨线程访问，需线程安全集合。 */
    private val inFlight = ConcurrentHashMap.newKeySet<Long>()

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
     * 分享进来：仅入库为待分析，不自动分析；由用户手动（详情页「开始分析」/卡片库「一键分析」）触发。
     * @return (卡片 id, 是否新建) — 相同文本已收录过则直接复用，不重复建卡。
     */
    suspend fun acceptShared(text: String, source: String): Pair<Long, Boolean> {
        val cleaned = cleanSharedText(text)
        findIdByText(cleaned)?.let { existing ->
            return existing to false
        }
        val id = cardDao.insert(
            Card(text = cleaned, source = source.ifBlank { "系统分享" }, status = CardStatus.PENDING)
        )
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

    /**
     * 补分析所有 待分析/失败/卡死 的卡片（「一键分析」入口）：并发 ANALYZE_CONCURRENCY 张，
     * 每完成一张回调进度（跳过 inFlight 已拦截的卡也计为处理过）。
     */
    suspend fun analyzePending(onProgress: (Int) -> Unit = {}) {
        val done = AtomicInteger(0)
        coroutineScope {
            cardDao.pendingCards().chunked(ANALYZE_CONCURRENCY).forEach { batch ->
                batch.map { card ->
                    async {
                        analyzeCard(card.id)
                        onProgress(done.incrementAndGet())
                    }
                }.awaitAll()
            }
        }
    }

    /**
     * 启动回填旧卡词条缺口（音标/级别）+ 归一化 LLM 合并格式音标：
     * - 老数据分析时词表尚未覆盖（如 loom 无音标）→ 查表补齐，幂等（无缺口时零更新）；
     *   音标兜底跳过词表空音标词条继续查变形原形（crouched → crouch）。
     * - LLM 偶发把「英 /x/，美 /y/」合并写进单字段 → 拆到 phonetic / phoneticUs 两列。
     * - 词表升级为英/美双音标后补美音缺口；旧版词表音标列实为美音，
     *   当库内音标与美音相同而英音不同时纠正为英音（避免「英 /wɑnd/」标错）。
     * 四级基础词按原规则不补级别。
     */
    suspend fun backfillWordGaps() {
        val rows = wordDao.withMissingMeta() + wordDao.withCombinedPhonetic()
        if (rows.isEmpty()) return
        val updates = ArrayList<CardWord>()
        for (cw in rows) {
            var changed = false
            val (uk, us) = AnalysisParser.splitPhoneticField(cw.phonetic)
            var newUs = cw.phoneticUs ?: us
            if (uk != null && uk != cw.phonetic) changed = true
            if (newUs != cw.phoneticUs) changed = true
            val entry = wordLevels.lookup(cw.word)
            var phonetic = uk?.takeIf { it.isNotBlank() }
                ?: wordLevels.phoneticOf(cw.word)?.also { changed = true }
            // 美音缺口：词表补（含变形原形回退）。
            if (newUs.isNullOrBlank()) {
                wordLevels.phoneticUsOf(cw.word)?.let { usOf ->
                    newUs = usOf
                    changed = true
                    // 库内音标与美音相同（旧版词表实为美音）且英音不同 → 纠正为英音。
                    if (phonetic != null && phonetic == usOf) {
                        wordLevels.phoneticOf(cw.word)?.let { ukOf ->
                            if (ukOf != usOf) {
                                phonetic = ukOf
                                changed = true
                            }
                        }
                    }
                }
            }
            val level = cw.level?.takeIf { it.isNotEmpty() }
                ?: entry?.level?.takeIf { it.isNotEmpty() && !entry.base }?.also { changed = true }
            if (changed) updates.add(cw.copy(phonetic = phonetic, phoneticUs = newUs, level = level))
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
     * 网络类失败 → PENDING（可「一键分析」重试）；解析/服务端类失败 → FAILED（可手动重试）。
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
     *    每项一条；音标（英/美）/级别/原型/词性/释义均 LLM 优先、命中级别表则词表兜底。
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
                // 难度判断交给 LLM（prompt 含学习者水平基线：超纲必挑、四级词语境用法值得学仍挑），
                // 词表四级标志不再剔除 LLM 关键词：3761 个四级基础词全部带考试级别（resign=雅思 等），
                // 一刀切按 base 剔除会误伤超纲词（曾导致 resign 类漏词）。词表未收录的词保留。
                if (entry != null && entry.base && entry.level.isEmpty()) continue
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
                        // 各元素 LLM 优先、词表兜底、再无则 null（UI 不显示）。
                        // 音标分英/美两列：LLM 缺时词表双音标兜底（英/美各查，含变形原形回退）；
                        // phoneticOf 跳过词表空音标词条继续查变形原形（crouched → crouch）。
                        phonetic = k.phoneticUk?.takeIf { it.isNotBlank() }
                            ?: wordLevels.phoneticOf(part),
                        phoneticUs = k.phoneticUs?.takeIf { it.isNotBlank() }
                            ?: wordLevels.phoneticUsOf(part),
                        pos = if (!k.pos.isNullOrBlank()) k.pos else posFromMeaning,
                        meaningInContext = meaning,
                        orderIdx = idx++,
                        level = k.level?.takeIf { it.isNotBlank() }
                            ?: entry?.level?.takeIf { it.isNotEmpty() },
                        lemma = k.lemma?.takeIf { it.isNotBlank() }
                            ?: wordLevels.lemma(part),
                        affix = encode(k.affix?.map { listOf(it.part, it.type.orEmpty(), it.meaning.orEmpty()) }.orEmpty()),
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
                        phonetic = wordLevels.phoneticOf(t.value),
                        phoneticUs = wordLevels.phoneticUsOf(t.value),
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

    // ---------- 备份 ----------

    /**
     * 导出备份（zip：readlet.db + readlet.xml 设置）。
     * 先 WAL checkpoint 把未落盘数据折进主库，保证拷出的 readlet.db 是完整快照。
     */
    suspend fun exportBackup(uri: Uri) = withContext(Dispatchers.IO) {
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").close()
        val dbFile = context.getDatabasePath("readlet.db")
        val prefsFile = File(context.dataDir, "shared_prefs/readlet.xml")
        context.contentResolver.openOutputStream(uri)?.use { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("readlet.db"))
                dbFile.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("readlet.xml"))
                prefsFile.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /**
     * 导入备份：解压 → 只读打开备份库（按列名读取，兼容旧版本缺列）→ 事务内整体替换 4 张表 → 恢复设置。
     * 行级还原而非文件覆盖：不关库、不重建实例，Room 观察流自动跟随新数据；失败整体回滚。
     */
    suspend fun importBackup(uri: Uri) = withContext(Dispatchers.IO) {
        val tmpDir = File(context.cacheDir, "import").apply { deleteRecursively(); mkdirs() }
        try {
            val tmpDb = File(tmpDir, "readlet.db")
            val tmpPrefs = File(tmpDir, "readlet.xml")
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        when (entry.name) {
                            "readlet.db" -> zip.copyTo(FileOutputStream(tmpDb))
                            "readlet.xml" -> zip.copyTo(FileOutputStream(tmpPrefs))
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
            if (!tmpDb.isFile || tmpDb.length() == 0L) {
                throw IllegalStateException("备份文件中没有 readlet.db")
            }
            val cards = readBackupCards(tmpDb)
            val words = readBackupWords(tmpDb)
            val logs = readBackupLogs(tmpDb)
            val usages = readBackupUsages(tmpDb)
            db.withTransaction {
                cardDao.deleteAll()
                wordDao.deleteAll()
                logDao.deleteAll()
                llmDao.deleteAll()
                if (cards.isNotEmpty()) cardDao.insertAll(cards)
                if (words.isNotEmpty()) wordDao.insertAll(words)
                if (logs.isNotEmpty()) logDao.insertAll(logs)
                if (usages.isNotEmpty()) llmDao.insertAll(usages)
            }
            if (tmpPrefs.isFile) restorePrefs(tmpPrefs)
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    private fun openBackup(file: File): SQLiteDatabase =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)

    /** 备份库只读使用：SQLiteDatabase 不实现 Closeable，用显式 try/finally。 */
    private fun <T> withBackup(file: File, block: (SQLiteDatabase) -> T): T {
        val db = openBackup(file)
        try {
            return block(db)
        } finally {
            db.close()
        }
    }

    /** 旧版本备份缺列时返回 null（当前列名读取，向后兼容）。 */
    private fun Cursor.stringOrNull(col: String): String? =
        getColumnIndex(col).takeIf { it >= 0 }?.let { if (isNull(it)) null else getString(it) }

    private fun Cursor.longOrNull(col: String): Long? =
        getColumnIndex(col).takeIf { it >= 0 }?.let { if (isNull(it)) null else getLong(it) }

    private fun readBackupCards(file: File): List<Card> = withBackup(file) { src ->
        src.rawQuery("SELECT * FROM cards", null).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Card(
                            id = c.getLong(c.getColumnIndexOrThrow("id")),
                            text = c.getString(c.getColumnIndexOrThrow("text")),
                            source = c.getString(c.getColumnIndexOrThrow("source")),
                            status = c.getInt(c.getColumnIndexOrThrow("status")),
                            translation = c.stringOrNull("translation"),
                            pointsJson = c.stringOrNull("pointsJson"),
                            grammarJson = c.stringOrNull("grammarJson"),
                            collocationsJson = c.stringOrNull("collocationsJson"),
                            createdAt = c.getLong(c.getColumnIndexOrThrow("createdAt")),
                            analyzedAt = c.longOrNull("analyzedAt"),
                            ef = c.getDouble(c.getColumnIndexOrThrow("ef")),
                            interval = c.getInt(c.getColumnIndexOrThrow("interval")),
                            reps = c.getInt(c.getColumnIndexOrThrow("reps")),
                            lapses = c.getInt(c.getColumnIndexOrThrow("lapses")),
                            dueAt = c.longOrNull("dueAt"),
                            mastered = c.getInt(c.getColumnIndexOrThrow("mastered")) != 0,
                        )
                    )
                }
            }
        }
    }

    private fun readBackupWords(file: File): List<CardWord> = withBackup(file) { src ->
        src.rawQuery("SELECT * FROM card_words", null).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        CardWord(
                            id = c.getLong(c.getColumnIndexOrThrow("id")),
                            cardId = c.getLong(c.getColumnIndexOrThrow("cardId")),
                            word = c.getString(c.getColumnIndexOrThrow("word")),
                            phonetic = c.stringOrNull("phonetic"),
                            phoneticUs = c.stringOrNull("phoneticUs"),
                            pos = c.stringOrNull("pos"),
                            meaningInContext = c.stringOrNull("meaningInContext"),
                            orderIdx = c.getInt(c.getColumnIndexOrThrow("orderIdx")),
                            level = c.stringOrNull("level"),
                            lemma = c.stringOrNull("lemma"),
                            affix = c.stringOrNull("affix"),
                        )
                    )
                }
            }
        }
    }

    private fun readBackupLogs(file: File): List<ReviewLog> = withBackup(file) { src ->
        src.rawQuery("SELECT * FROM review_logs", null).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        ReviewLog(
                            id = c.getLong(c.getColumnIndexOrThrow("id")),
                            cardId = c.getLong(c.getColumnIndexOrThrow("cardId")),
                            grade = c.getInt(c.getColumnIndexOrThrow("grade")),
                            type = c.getInt(c.getColumnIndexOrThrow("type")),
                            reviewedAt = c.getLong(c.getColumnIndexOrThrow("reviewedAt")),
                            reviewedDay = c.getLong(c.getColumnIndexOrThrow("reviewedDay")),
                            intervalAfter = c.getInt(c.getColumnIndexOrThrow("intervalAfter")),
                        )
                    )
                }
            }
        }
    }

    private fun readBackupUsages(file: File): List<LlmUsage> = withBackup(file) { src ->
        src.rawQuery("SELECT * FROM llm_usage", null).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        LlmUsage(
                            id = c.getLong(c.getColumnIndexOrThrow("id")),
                            calledAt = c.getLong(c.getColumnIndexOrThrow("calledAt")),
                            promptTokens = c.getInt(c.getColumnIndexOrThrow("promptTokens")),
                            completionTokens = c.getInt(c.getColumnIndexOrThrow("completionTokens")),
                            totalTokens = c.getInt(c.getColumnIndexOrThrow("totalTokens")),
                        )
                    )
                }
            }
        }
    }

    /** 恢复设置（readlet.xml）：清空后按备份键值回填（当前设置全是字符串键值）。 */
    private fun restorePrefs(file: File) {
        val parser = Xml.newPullParser()
        parser.setInput(FileInputStream(file), "UTF-8")
        val entries = mutableListOf<Pair<String, String>>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "string") {
                parser.getAttributeValue(null, "name")?.let { name ->
                    entries += name to parser.nextText()
                }
            }
            event = parser.next()
        }
        if (entries.isEmpty()) return
        val editor = context.getSharedPreferences("readlet", Context.MODE_PRIVATE).edit().clear()
        entries.forEach { (k, v) -> editor.putString(k, v) }
        editor.apply()
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

    /** 下一张待学习卡（连播）：加入复习后直接切到它；null = 待学习已全部学完。 */
    suspend fun nextUnlearned(excludeId: Long): Card? = cardDao.nextUnlearned(excludeId)

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
    fun observeReviewCounts(): kotlinx.coroutines.flow.Flow<List<CardReviewCount>> = logDao.observeReviewCounts()
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

package com.readlet.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Insert
    suspend fun insert(card: Card): Long

    @Update
    suspend fun update(card: Card)

    @Delete
    suspend fun delete(card: Card)

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun byId(id: Long): Card?

    /** 单卡实时观察：详情页用，状态变化（如重新分析）即时反映。 */
    @Query("SELECT * FROM cards WHERE id = :id")
    fun observeById(id: Long): Flow<Card?>

    @Query("SELECT * FROM cards ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Card>>

    @Query("SELECT id FROM cards WHERE text = :text COLLATE NOCASE LIMIT 1")
    suspend fun findIdByText(text: String): Long?

    /** 全部 (id, text)：去重时扁平化比对旧数据（老卡片换行已被压成空格）。 */
    @Query("SELECT id, text FROM cards")
    suspend fun allIdTexts(): List<CardIdText>

    // 待补分析：待分析/失败/卡死在分析中（进程被杀）的卡片。
    // 正常分析中的卡片由仓库层 inFlight 集合拦截，不会被重复拉取。
    @Query("SELECT * FROM cards WHERE status IN (0, 1, 3) ORDER BY createdAt DESC")
    fun observeNeedAnalysis(): Flow<List<Card>>

    @Query("SELECT * FROM cards WHERE status IN (0, 1, 3) ORDER BY createdAt DESC")
    suspend fun pendingCards(): List<Card>

    // 复习队列只含已点「开始学习」的卡片（dueAt 已排程，≤今天到期）；
    // 待学习（dueAt=null）/ 待分析 / 已掌握 一律不进队列。
    @Query(
        "SELECT * FROM cards WHERE status = 2 AND mastered = 0 " +
            "AND dueAt IS NOT NULL AND dueAt <= :today ORDER BY dueAt ASC"
    )
    suspend fun dueQueue(today: Long): List<Card>

    @Query("SELECT COUNT(*) FROM cards WHERE analyzedAt IS NOT NULL")
    fun observeAnalyzedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM cards")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT * FROM cards WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<Card>
}

/** Room 结果映射：id + 原文（去重用）。 */
data class CardIdText(val id: Long, val text: String)

@Dao
interface CardWordDao {
    @Insert
    suspend fun insertAll(words: List<CardWord>)

    /** 重新分析前清空该卡的旧词标注（防止重分析累积重复词）。 */
    @Query("DELETE FROM card_words WHERE cardId = :cardId")
    suspend fun deleteByCard(cardId: Long)

    @Query("SELECT * FROM card_words WHERE cardId = :cardId ORDER BY orderIdx ASC")
    suspend fun byCard(cardId: Long): List<CardWord>

    @Query("SELECT * FROM card_words WHERE cardId IN (:ids) ORDER BY orderIdx ASC")
    suspend fun byCards(ids: List<Long>): List<CardWord>

    /** 音标/级别空缺的词条（词表升级后启动回填用）。 */
    @Query(
        "SELECT * FROM card_words WHERE phonetic IS NULL OR phonetic = '' " +
            "OR level IS NULL OR level = ''"
    )
    suspend fun withMissingMeta(): List<CardWord>

    @Update
    suspend fun updateAll(words: List<CardWord>)

    /** 全量词表：统计词频榜/按词查句在 Kotlin 侧拆分重聚合（数据量小，免迁移旧粘连词）。 */
    @Query("SELECT * FROM card_words")
    suspend fun allWords(): List<CardWord>
}

data class WordFreq(val word: String, val freq: Int, val cards: Int)

@Dao
interface ReviewLogDao {
    @Insert
    suspend fun insert(log: ReviewLog)

    @Query("SELECT reviewedDay, COUNT(*) AS cnt FROM review_logs GROUP BY reviewedDay")
    fun observeDailyCounts(): Flow<List<DailyCount>>

    @Query("SELECT COUNT(*) FROM review_logs WHERE type = :type")
    fun observeCountByType(type: Int): Flow<Int>

    @Query("SELECT * FROM review_logs WHERE cardId = :cardId ORDER BY reviewedAt DESC LIMIT 1")
    suspend fun lastForCard(cardId: Long): ReviewLog?
}

data class DailyCount(val reviewedDay: Long, val cnt: Int)

@Dao
interface LlmUsageDao {
    @Insert
    suspend fun insert(usage: LlmUsage)

    /** 累计调用次数（成功调用）。 */
    @Query("SELECT COUNT(*) FROM llm_usage")
    fun observeCallCount(): Flow<Int>

    /** 累计 token 用量。 */
    @Query("SELECT COALESCE(SUM(totalTokens), 0) FROM llm_usage")
    fun observeTotalTokens(): Flow<Int>
}

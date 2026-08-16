package com.readlet.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 卡片状态 */
object CardStatus {
    const val PENDING = 0      // 待分析（离线/未联网）
    const val ANALYZING = 1    // 分析中
    const val ANALYZED = 2     // 已分析
    const val FAILED = 3       // 分析失败（可手动重试）
}

/** 复习日志类型 */
object ReviewType {
    const val SCHEDULED = 0    // 排程复习（写入 SRS 字段）
    const val PRACTICE = 1     // 加练（不写入 SRS 字段）
}

/** 句子卡片：复习的主体 */
@Entity(tableName = "cards")
data class Card(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val source: String,
    val status: Int = CardStatus.PENDING,
    val translation: String? = null,
    val pointsJson: String? = null,
    val grammarJson: String? = null,
    val collocationsJson: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val analyzedAt: Long? = null,
    // SM-2 排程字段
    val ef: Double = 2.5,
    val interval: Int = 0,
    val reps: Int = 0,
    val lapses: Int = 0,
    val dueAt: Long? = null, // epochDay；null=待学习（已分析但未点「开始学习」，不进复习队列）
    val mastered: Boolean = false, // 已掌握：不再进入复习队列
)

/** 卡片的重点词标注：词是索引（GROUP BY word 得词频） */
@Entity(
    tableName = "card_words",
    foreignKeys = [
        ForeignKey(
            entity = Card::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("cardId"), Index("word")],
)
data class CardWord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long,
    val word: String,
    val phonetic: String? = null,
    val pos: String? = null,
    val meaningInContext: String? = null,
    val orderIdx: Int = 0,
    /** 考试级别角标（v4）：六级/考研/雅思/专四/专八；空 = 无级别。LLM 重点词命中级别表也补标。 */
    val level: String? = null,
)

/** 复习日志：SRS 排程与统计（热力图/曲线/打卡）的数据源 */
@Entity(
    tableName = "review_logs",
    foreignKeys = [
        ForeignKey(
            entity = Card::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("cardId"), Index("reviewedDay")],
)
data class ReviewLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long,
    val grade: Int,               // SM-2 quality: 1/3/4/5
    val type: Int = ReviewType.SCHEDULED,
    val reviewedAt: Long = System.currentTimeMillis(),
    val reviewedDay: Long,        // LocalDate.toEpochDay()
    val intervalAfter: Int = 0,
)

/** LLM 调用记录：统计页「调用次数 / Token 用量」的数据源（一次成功调用一行）。 */
@Entity(tableName = "llm_usage")
data class LlmUsage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val calledAt: Long = System.currentTimeMillis(),
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
)

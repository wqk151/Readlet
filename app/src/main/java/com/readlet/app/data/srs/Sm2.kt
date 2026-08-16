package com.readlet.app.data.srs

import com.readlet.app.data.db.Card

/** SM-2 间隔重复算法（纯 Kotlin，无 Android 依赖，可单元测试）。 */
object Sm2 {

    /** 评级 → SM-2 quality：重来=1 困难=3 良好=4 简单=5 */
    fun qualityOf(grade: Int): Int = when (grade) {
        3 -> 3
        4 -> 4
        5 -> 5
        else -> 1
    }

    /**
     * 依据一次评级计算新的排程字段。
     * 返回 (ef, interval, reps, lapses)。
     */
    fun next(ef: Double, interval: Int, reps: Int, lapses: Int, quality: Int): SrsState {
        val q = qualityOf(quality)
        if (q < 3) {
            return SrsState(
                ef = ef,
                interval = 1,
                reps = 0,
                lapses = lapses + 1,
            )
        }
        val newEf = (ef + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02))).coerceAtLeast(1.3)
        val newReps = reps + 1
        val newInterval = when {
            newReps == 1 -> 1
            newReps == 2 -> 6
            else -> Math.round(interval * newEf).toInt().coerceAtLeast(1)
        }
        return SrsState(newEf, newInterval, newReps, lapses)
    }

    /** 应用评级到卡片并返回新卡片（不持久化，由调用方负责写库与记日志）。 */
    fun apply(card: Card, quality: Int): Card {
        val s = next(card.ef, card.interval, card.reps, card.lapses, quality)
        val today = java.time.LocalDate.now().toEpochDay()
        return card.copy(
            ef = s.ef,
            interval = s.interval,
            reps = s.reps,
            lapses = s.lapses,
            dueAt = if (s.interval <= 0) null else today + s.interval,
        )
    }
}

data class SrsState(
    val ef: Double,
    val interval: Int,
    val reps: Int,
    val lapses: Int,
)

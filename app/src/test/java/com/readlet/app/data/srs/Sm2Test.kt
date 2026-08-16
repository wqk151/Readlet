package com.readlet.app.data.srs

import com.readlet.app.data.db.Card
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Sm2Test {

    @Test
    fun `first success interval is 1`() {
        val s = Sm2.next(2.5, 0, 0, 0, 4)
        assertEquals(1, s.interval)
        assertEquals(1, s.reps)
        assertEquals(2.5, s.ef, 0.0001)
    }

    @Test
    fun `second success interval is 6`() {
        val s = Sm2.next(2.5, 1, 1, 0, 4)
        assertEquals(6, s.interval)
        assertEquals(2, s.reps)
    }

    @Test
    fun `interval grows by ef after second success`() {
        // reps=2 interval=6 → 第三次 success: round(6 * ef)
        val s = Sm2.next(2.5, 6, 2, 0, 4)
        assertEquals(15, s.interval) // 6 * 2.5 = 15
    }

    @Test
    fun `hard decreases ef but stays above floor`() {
        val s = Sm2.next(2.5, 6, 2, 0, 3)
        // ef = 2.5 + (0.1 - 2*(0.08+2*0.02)) = 2.5 + (0.1 - 0.24) = 2.36
        assertEquals(2.36, s.ef, 0.0001)
        assertTrue(s.ef >= 1.3)
    }

    @Test
    fun `ef never goes below 1_3`() {
        var s = SrsState(2.5, 6, 2, 0)
        repeat(10) { s = Sm2.next(s.ef, s.interval, s.reps, s.lapses, 3) }
        assertTrue(s.ef >= 1.3)
    }

    @Test
    fun `again resets reps and interval and increments lapses`() {
        val s = Sm2.next(2.5, 15, 3, 0, 1)
        assertEquals(0, s.reps)
        assertEquals(1, s.interval)
        assertEquals(1, s.lapses)
    }

    @Test
    fun `easy gives quality 5 and bigger interval`() {
        val s = Sm2.next(2.5, 15, 3, 0, 5)
        assertEquals(4, s.reps)
        // ef = 2.5 + 0.1 = 2.6
        assertEquals(2.6, s.ef, 0.0001)
    }

    @Test
    fun `apply sets dueAt to today plus interval`() {
        val today = java.time.LocalDate.now().toEpochDay()
        val card = Card(text = "t", source = "s", ef = 2.5, interval = 0, reps = 0)
        val newCard = Sm2.apply(card, 4)
        assertEquals(1, newCard.interval)
        assertEquals(today + 1, newCard.dueAt)
        assertEquals(1, newCard.reps)
    }

    @Test
    fun `quality mapping`() {
        assertEquals(1, Sm2.qualityOf(1))
        assertEquals(3, Sm2.qualityOf(3))
        assertEquals(4, Sm2.qualityOf(4))
        assertEquals(5, Sm2.qualityOf(5))
        assertEquals(1, Sm2.qualityOf(99)) // 未知评级按失败处理
    }
}

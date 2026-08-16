package com.readlet.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SearchMatcherTest {

    // ---------- 回归：跨词子序列不再命中 ----------

    @Test
    fun `couple does not match words without it`() {
        // 旧 fzf 式实现会让 "couple" 命中这些词（子序列散布），词级匹配必须全部不命中。
        for (w in listOf("course", "up", "corners", "left", "out", "corridors")) {
            assertNull("$w should not match 'couple'", SearchMatcher.match("couple", w))
        }
    }

    @Test
    fun `couple does not match long paragraph without the word`() {
        val text = "The club flew suddenly out of the troll's hand, rose high into the air, " +
            "turned slowly over — and dropped, with a sickening crack, onto its owner's head."
        assertNull(SearchMatcher.match("couple", text))
    }

    // ---------- 词级命中与打分 ----------

    @Test
    fun `exact word scores 100`() {
        val r = SearchMatcher.match("couple", "A couple of students ran out.")
        assertNotNull(r)
        assertEquals(100, r!!.score)
        assertEquals(2..7, r.ranges.single()) // 高亮整词 "couple"
    }

    @Test
    fun `prefix scores 60`() {
        val r = SearchMatcher.match("coupl", "A couple of students.")
        assertNotNull(r)
        assertEquals(60, r!!.score)
    }

    @Test
    fun `substring scores 30`() {
        val r = SearchMatcher.match("ouple", "A couple of students.")
        assertNotNull(r)
        assertEquals(30, r!!.score)
    }

    @Test
    fun `case insensitive`() {
        assertEquals(100, SearchMatcher.score("COUPLE", "a COUPLE of"))
        assertEquals(100, SearchMatcher.score("couple", "A COUPLE of"))
    }

    @Test
    fun `matched range covers whole word not just query`() {
        val r = SearchMatcher.match("cou", "A couple of students.")!!
        assertEquals("couple", "A couple of students.".substring(r.ranges.single()))
    }

    // ---------- 词组匹配 ----------

    @Test
    fun `phrase matches as word sequence`() {
        val r = SearchMatcher.match("got to his feet", "Harry got to his feet and ran.")
        assertNotNull(r)
        assertEquals(100, r!!.score)
        assertEquals("got to his feet", "Harry got to his feet and ran.".substring(r.ranges.single()))
    }

    @Test
    fun `phrase prefix matches`() {
        assertEquals(100, SearchMatcher.score("got to", "Harry got to his feet."))
    }

    @Test
    fun `phrase does not match across non-adjacent words`() {
        assertNull(SearchMatcher.match("got feet", "Harry got to his feet."))
    }

    @Test
    fun `phrase matches keyword field`() {
        // 重点词字段是词组时，查询词组直接命中。
        assertEquals(100, SearchMatcher.score("got to his feet", "got to his feet"))
    }

    // ---------- 边界 ----------

    @Test
    fun `empty or blank query returns null`() {
        assertNull(SearchMatcher.match("", "anything"))
        assertNull(SearchMatcher.match("   ", "anything"))
    }

    @Test
    fun `query punctuation trimmed`() {
        assertEquals(100, SearchMatcher.score("couple,", "a couple of"))
        assertEquals(100, SearchMatcher.score("「couple」", "a couple of"))
    }

    @Test
    fun `score zero when no match`() {
        assertEquals(0, SearchMatcher.score("zzzz", "nothing here"))
    }
}

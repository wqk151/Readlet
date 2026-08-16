package com.readlet.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeywordsTest {

    @Test
    fun `splits comma joined parallel structure`() {
        assertEquals(
            listOf("bottle fame", "brew glory", "stopper death"),
            Keywords.splitKeyword("bottle fame, brew glory, stopper death")
        )
    }

    @Test
    fun `splits chinese separators and slashes`() {
        assertEquals(listOf("up", "down"), Keywords.splitKeyword("up、down"))
        assertEquals(listOf("up", "down"), Keywords.splitKeyword("up/down"))
    }

    @Test
    fun `strips trailing punctuation`() {
        assertEquals(listOf("sweeping"), Keywords.splitKeyword("sweeping."))
        assertEquals(listOf("up"), Keywords.splitKeyword("up…"))
        assertEquals(listOf("him"), Keywords.splitKeyword("him”"))
    }

    @Test
    fun `drops empty and single char parts`() {
        assertEquals(listOf("up", "down"), Keywords.splitKeyword("up, , down"))
        assertEquals(emptyList<String>(), Keywords.splitKeyword("a"))
    }

    @Test
    fun `phrase without separators stays intact`() {
        assertEquals(listOf("got to his feet"), Keywords.splitKeyword("got to his feet"))
    }

    @Test
    fun `splitKeywords dedupes case-insensitively keeping order`() {
        assertEquals(
            listOf("Couple", "of"),
            Keywords.splitKeywords(listOf("Couple, of", "couple"))
        )
    }

    @Test
    fun `function words detected`() {
        assertTrue(Keywords.isFunctionWord("were"))
        assertTrue(Keywords.isFunctionWord("As"))
        assertTrue(Keywords.isFunctionWord("EVEN"))
        assertFalse(Keywords.isFunctionWord("hurtle"))
        assertFalse(Keywords.isFunctionWord("ravine"))
    }

    @Test
    fun `level label maps exam levels to short markers`() {
        assertEquals("CET6", Keywords.levelLabel("六级"))
        assertEquals("CET4", Keywords.levelLabel("四级"))
        assertEquals("TEM4", Keywords.levelLabel("专四"))
        assertEquals("TEM8", Keywords.levelLabel("专八"))
        assertEquals("IELTS", Keywords.levelLabel("雅思"))
        assertEquals("考研", Keywords.levelLabel("考研"))
    }

    @Test
    fun `level label trims and hides absent levels`() {
        assertNull(Keywords.levelLabel(null))
        assertNull(Keywords.levelLabel(""))
        assertNull(Keywords.levelLabel("   "))
        assertEquals("CET6", Keywords.levelLabel(" 六级 "))
    }

    @Test
    fun `unknown level falls back to raw value`() {
        assertEquals("GRE", Keywords.levelLabel("GRE"))
    }

    @Test
    fun `splitPos extracts single pos prefix`() {
        assertEquals("n." to "峡谷", Keywords.splitPos("n. 峡谷"))
        assertEquals("adj." to "高的；年长的", Keywords.splitPos("adj. 高的；年长的"))
    }

    @Test
    fun `splitPos extracts compound pos prefixes`() {
        assertEquals("vt., vi." to "= brutalize", Keywords.splitPos("vt., vi. = brutalize"))
        assertEquals("n., adj." to "Azania的变形", Keywords.splitPos("n., adj. Azania的变形"))
    }

    @Test
    fun `splitPos keeps meaning without pos prefix untouched`() {
        assertEquals(null to "隐隐呈现，赫然耸现", Keywords.splitPos("隐隐呈现，赫然耸现"))
        assertEquals(null to "[数] 矩阵", Keywords.splitPos("[数] 矩阵"))
        assertEquals(null to "", Keywords.splitPos(""))
    }

    @Test
    fun `splitPos only strips leading pos`() {
        // 多词性混排只拆开头，剩余保留原文（v. 部分属于释义内容）
        assertEquals("n." to "银行；岸,堤； v. (on)把…基于", Keywords.splitPos("n. 银行；岸,堤； v. (on)把…基于"))
    }
}

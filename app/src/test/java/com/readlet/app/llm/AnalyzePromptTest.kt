package com.readlet.app.llm

import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyzePromptTest {

    @Test
    fun `parses arrow separator`() {
        assertEquals(
            listOf("tolerance → 公差"),
            AnalyzePrompt.parseGlossary("tolerance → 公差")
        )
    }

    @Test
    fun `parses colon and equals separators`() {
        assertEquals(
            listOf("clearance → 间隙", "tolerance → 公差"),
            AnalyzePrompt.parseGlossary("clearance: 间隙\ntolerance = 公差")
        )
    }

    @Test
    fun `drops blank lines and lines without separator`() {
        assertEquals(
            listOf("a → b"),
            AnalyzePrompt.parseGlossary("a → b\n\n没有分隔符的一行")
        )
    }

    @Test
    fun `drops empty term or translation`() {
        assertEquals(
            listOf("a → b"),
            AnalyzePrompt.parseGlossary("a → b\n → 空术语\n a → ")
        )
    }

    @Test
    fun `blank glossary yields empty list`() {
        assertEquals(emptyList<String>(), AnalyzePrompt.parseGlossary(""))
        assertEquals(emptyList<String>(), AnalyzePrompt.parseGlossary("   \n  "))
    }
}

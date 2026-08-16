package com.readlet.app.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisParserTest {

    @Test
    fun `parses sentence mode json`() {
        val raw = """
            {"mode":"sentence","translation":"雾从海面涌来，压低了远处港口的灯火。",
             "keywords":[{"word":"muffling","phonetic":"/ˈmʌflɪŋ/","pos":"v.","meaning_in_context":"使(声音/光线)变低沉；此处指雾气模糊了灯光"}],
             "points":[{"expr":"muffling the harbor lights","meaning":"现在分词伴随状语"}],
             "grammar":[{"structure":"The fog rolled in","explanation":"主干：主语+谓语"}],
             "collocations":[{"phrase":"roll in","meaning":"(雾/云)滚滚而来","example":"Dark clouds rolled in.","example_cn":"乌云滚滚而来。"}]}
        """.trimIndent()
        val r = AnalysisParser.parse(raw)
        assertEquals("sentence", r.mode)
        assertEquals("雾从海面涌来，压低了远处港口的灯火。", r.translation)
        assertEquals(1, r.keywords.size)
        assertEquals("muffling", r.keywords[0].word)
        assertEquals("/ˈmʌflɪŋ/", r.keywords[0].phonetic)
        assertEquals("v.", r.keywords[0].pos)
        assertEquals(1, r.grammar.size)
        assertEquals("roll in", r.collocations[0].phrase)
        assertEquals("Dark clouds rolled in.", r.collocations[0].example)
    }

    @Test
    fun `strips markdown code fences`() {
        val raw = """
            ```json
            {"mode":"word","translation":null,
             "keywords":[{"word":"harbor","phonetic":"/ˈhɑːrbər/","pos":"n.","meaning_in_context":"港口"}],
             "points":[],"grammar":[],"collocations":[]}
            ```
        """.trimIndent()
        val r = AnalysisParser.parse(raw)
        assertEquals("word", r.mode)
        assertNull(r.translation)
        assertEquals("harbor", r.keywords[0].word)
        assertTrue(r.points.isEmpty())
    }

    @Test
    fun `tolerates missing optional fields`() {
        val raw = """{"mode":"sentence","translation":"译文","keywords":[{"word":"x"}]}"""
        val r = AnalysisParser.parse(raw)
        assertEquals("译文", r.translation)
        assertNull(r.keywords[0].phonetic)
        assertNull(r.keywords[0].meaningInContext)
        assertTrue(r.grammar.isEmpty())
    }

    @Test
    fun `skips broken entries but keeps valid ones`() {
        val raw = """
            {"mode":"sentence","translation":"t",
             "keywords":[{"word":"good","meaning_in_context":"m"},
                         {"word":"broken"}],
             "grammar":[],"collocations":[]}
        """.trimIndent()
        // 第二条缺少 word 字段 → optString 返回 ""（不会抛异常，被保留为空词）
        val r = AnalysisParser.parse(raw)
        assertEquals(2, r.keywords.size)
        assertEquals("good", r.keywords[0].word)
    }

    @Test
    fun `word mode keeps single keyword`() {
        val raw = """
            {"mode":"phrase","translation":null,
             "keywords":[{"word":"take care of","pos":"phr.","meaning_in_context":"照顾"}],
             "points":[],"grammar":[],
             "collocations":[{"phrase":"take care of","meaning":"照顾","example":"She took care of the baby.","example_cn":"她照顾宝宝。"}]}
        """.trimIndent()
        val r = AnalysisParser.parse(raw)
        assertEquals("phrase", r.mode)
        assertEquals("take care of", r.keywords[0].word)
        assertEquals("She took care of the baby.", r.collocations[0].example)
    }
}

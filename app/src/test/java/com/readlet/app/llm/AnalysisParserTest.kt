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
             "keywords":[{"word":"muffling","phonetic_uk":"/ˈmʌflɪŋ/","phonetic_us":"/ˈmʌflɪŋ/","pos":"v.","level":"六级","lemma":"muffle","meaning_in_context":"使(声音/光线)变低沉；此处指雾气模糊了灯光"}],
             "points":[{"expr":"muffling the harbor lights","meaning":"现在分词伴随状语"}],
             "grammar":[{"structure":"The fog rolled in","explanation":"主干：主语+谓语"}],
             "collocations":[{"phrase":"roll in","meaning":"(雾/云)滚滚而来","example":"Dark clouds rolled in.","example_cn":"乌云滚滚而来。"}]}
        """.trimIndent()
        val r = AnalysisParser.parse(raw)
        assertEquals("sentence", r.mode)
        assertEquals("雾从海面涌来，压低了远处港口的灯火。", r.translation)
        assertEquals(1, r.keywords.size)
        assertEquals("muffling", r.keywords[0].word)
        assertEquals("/ˈmʌflɪŋ/", r.keywords[0].phoneticUk)
        assertEquals("/ˈmʌflɪŋ/", r.keywords[0].phoneticUs)
        assertEquals("v.", r.keywords[0].pos)
        assertEquals("六级", r.keywords[0].level)
        assertEquals("muffle", r.keywords[0].lemma)
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
        // 旧 prompt 的 phonetic 归入英式音标
        assertEquals("/ˈhɑːrbər/", r.keywords[0].phoneticUk)
        assertNull(r.keywords[0].phoneticUs)
        assertTrue(r.points.isEmpty())
    }

    @Test
    fun `tolerates missing optional fields`() {
        val raw = """{"mode":"sentence","translation":"译文","keywords":[{"word":"x"}]}"""
        val r = AnalysisParser.parse(raw)
        assertEquals("译文", r.translation)
        assertNull(r.keywords[0].phoneticUk)
        assertNull(r.keywords[0].phoneticUs)
        assertNull(r.keywords[0].level)
        assertNull(r.keywords[0].lemma)
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

    // ---------- 音标字段归一化 ----------

    @Test
    fun `splits merged labeled phonetics`() {
        val (uk, us) = AnalysisParser.splitPhoneticField("英 /ɡɒt tə hɪz fiːt/，美 /ɡɑːt tə hɪz fiːt/")
        assertEquals("/ɡɒt tə hɪz fiːt/", uk)
        assertEquals("/ɡɑːt tə hɪz fiːt/", us)
    }

    @Test
    fun `splits english labeled phonetics case-insensitive`() {
        val (uk, us) = AnalysisParser.splitPhoneticField("UK: /wɒnd/ US: /wɑːnd/")
        assertEquals("/wɒnd/", uk)
        assertEquals("/wɑːnd/", us)
        val (uk2, us2) = AnalysisParser.splitPhoneticField("英式 /rəʊb/ 美式 /roʊb/")
        assertEquals("/rəʊb/", uk2)
        assertEquals("/roʊb/", us2)
    }

    @Test
    fun `splits bare multiple ipas in order`() {
        val (uk, us) = AnalysisParser.splitPhoneticField("/wɒnd/ /wɑːnd/")
        assertEquals("/wɒnd/", uk)
        assertEquals("/wɑːnd/", us)
    }

    @Test
    fun `bare single phonetic stays uk`() {
        val (uk, us) = AnalysisParser.splitPhoneticField("/ˈmʌflɪŋ/")
        assertEquals("/ˈmʌflɪŋ/", uk)
        assertNull(us)
    }

    @Test
    fun `blank and garbage phonetics pass through`() {
        val (uk1, us1) = AnalysisParser.splitPhoneticField("")
        assertNull(uk1)
        assertNull(us1)
        val (uk2, us2) = AnalysisParser.splitPhoneticField("英式读音")
        assertEquals("英式读音", uk2)
        assertNull(us2)
    }

    @Test
    fun `merged format in single field splits at parse time`() {
        val raw = """
            {"mode":"sentence","translation":"译文",
             "keywords":[{"word":"wand","phonetic_uk":"英 /wɒnd/，美 /wɑːnd/"}],
             "points":[],"grammar":[],"collocations":[]}
        """.trimIndent()
        val r = AnalysisParser.parse(raw)
        assertEquals("/wɒnd/", r.keywords[0].phoneticUk)
        assertEquals("/wɑːnd/", r.keywords[0].phoneticUs)
    }

    @Test
    fun `explicit phonetic_us wins over merged split`() {
        val raw = """
            {"mode":"sentence","translation":"译文",
             "keywords":[{"word":"wand","phonetic_uk":"英 /wɒnd/，美 /wɑːnd/","phonetic_us":"/wɑːnd/"}],
             "points":[],"grammar":[],"collocations":[]}
        """.trimIndent()
        val r = AnalysisParser.parse(raw)
        assertEquals("/wɒnd/", r.keywords[0].phoneticUk)
        assertEquals("/wɑːnd/", r.keywords[0].phoneticUs)
    }

    // ---------- 词根词缀 ----------

    @Test
    fun `parses affix part array`() {
        val raw = """
            {"mode":"sentence","translation":"t",
             "keywords":[{"word":"sneaking","affix":[{"part":"sneak","type":"词根","meaning":"偷偷走"},
                                                       {"part":"-ing","type":"后缀","meaning":"进行中"}]}],
             "points":[],"grammar":[],"collocations":[]}
        """.trimIndent()
        val affix = AnalysisParser.parse(raw).keywords[0].affix!!
        assertEquals(2, affix.size)
        assertEquals("sneak", affix[0].part)
        assertEquals("词根", affix[0].type)
        assertEquals("偷偷走", affix[0].meaning)
        assertEquals("-ing", affix[1].part)
        assertEquals("后缀", affix[1].type)
        assertEquals("进行中", affix[1].meaning)
    }

    @Test
    fun `legacy string affix becomes single part`() {
        val raw = """
            {"mode":"sentence","translation":"t",
             "keywords":[{"word":"loomed","affix":"loom（词根）+ -ed（过去式后缀）"}],
             "points":[],"grammar":[],"collocations":[]}
        """.trimIndent()
        val affix = AnalysisParser.parse(raw).keywords[0].affix!!
        assertEquals(1, affix.size)
        assertEquals("loom（词根）+ -ed（过去式后缀）", affix[0].part)
        assertNull(affix[0].type)
        assertNull(affix[0].meaning)
    }

    @Test
    fun `missing affix stays null`() {
        val raw = """{"mode":"sentence","translation":"t","keywords":[{"word":"x"}],"points":[],"grammar":[],"collocations":[]}"""
        assertNull(AnalysisParser.parse(raw).keywords[0].affix)
    }

    @Test
    fun `uncertain static fields parse to null not fabricated`() {
        val raw = """
            {"mode":"sentence","translation":"t",
             "keywords":[{"word":"x","phonetic_uk":null,"phonetic_us":null,"pos":null,"level":null,"lemma":null,"meaning_in_context":"m"}],
             "points":[],"grammar":[],"collocations":[]}
        """.trimIndent()
        val k = AnalysisParser.parse(raw).keywords[0]
        assertNull(k.phoneticUk)
        assertNull(k.phoneticUs)
        assertNull(k.pos)
        assertNull(k.level)
        assertNull(k.lemma)
    }
}

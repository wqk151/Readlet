package com.readlet.app.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EtymologyParserTest {

    @Test
    fun `parses etymology with breakdowns and derivation`() {
        val raw = """
            {"root":"port","meaning":"carry","origin":"Latin",
             "items":[
               {"word":"export","breakdown":[{"part":"ex-","type":"前缀","meaning":"向外"},
                                                {"part":"port","type":"词根","meaning":"搬运"}],
                "meaning":"搬出去 → 出口"},
               {"word":"disport","breakdown":null,"meaning":"娱乐；嬉戏"}
             ]}
        """.trimIndent()
        val r = EtymologyParser.parse(raw)
        assertEquals("port", r.root)
        assertEquals("carry", r.meaning)
        assertEquals("Latin", r.origin)
        assertEquals(2, r.items.size)
        assertEquals("export", r.items[0].word)
        assertEquals(2, r.items[0].breakdown!!.size)
        assertEquals("ex-", r.items[0].breakdown!![0].part)
        assertEquals("前缀", r.items[0].breakdown!![0].type)
        assertEquals("向外", r.items[0].breakdown!![0].meaning)
        assertEquals("搬出去 → 出口", r.items[0].meaning)
        assertNull(r.items[1].breakdown)
        assertEquals("娱乐；嬉戏", r.items[1].meaning)
    }

    @Test
    fun `encode and decode items round trip`() {
        val items = listOf(
            EtymologyItem("export", listOf(EtymologyPart("ex-", "前缀", "向外"), EtymologyPart("port", "词根", "搬运")), "搬出去 → 出口"),
            EtymologyItem("disport", null, "娱乐"),
        )
        val json = EtymologyParser.encodeItems(items)
        val decoded = EtymologyParser.decodeItems(json)
        assertEquals(2, decoded.size)
        assertEquals("export", decoded[0].word)
        assertEquals("向外", decoded[0].breakdown!![0].meaning)
        assertNull(decoded[1].breakdown)
        assertEquals("娱乐", decoded[1].meaning)
    }

    @Test
    fun `empty or missing items yields empty list`() {
        val r = EtymologyParser.parse("""{"root":"port","meaning":"carry","items":[]}""")
        assertTrue(r.items.isEmpty())
        assertTrue(EtymologyParser.decodeItems(null).isEmpty())
    }

    @Test
    fun `normalize unifies same component gloss across family`() {
        val items = listOf(
            EtymologyItem("deport", listOf(EtymologyPart("de-", "前缀", "离开"), EtymologyPart("port", "词根", "搬运")), "搬离"),
            EtymologyItem("deportation", listOf(EtymologyPart("de-", "前缀", "离开")), "移离"),
            EtymologyItem("deportment", listOf(EtymologyPart("de-", "前缀", "完全"), EtymologyPart("port", "词根", "携带")), "举止"),
        )
        val n = EtymologyParser.normalizeItems(items)
        // de- 多数义=离开 → 三处统一为"离开"（消除"完全"漂移）
        assertEquals("离开", n[0].breakdown!![0].meaning)
        assertEquals("离开", n[1].breakdown!![0].meaning)
        assertEquals("离开", n[2].breakdown!![0].meaning)
        // port 平手（搬运/携带各1）→ 取词族先出现者"搬运"
        assertEquals("搬运", n[0].breakdown!![1].meaning)
        assertEquals("搬运", n[2].breakdown!![1].meaning)
    }
}

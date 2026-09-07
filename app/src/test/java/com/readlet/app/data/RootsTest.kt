package com.readlet.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class RootsTest {

    // 最小可用数据：port 词根 + 若干前缀/后缀，验证三层索引与拆解推导。
    private val sample = """
        {
          "port": {"meaning":"carry","class":"root","root":"port","origin":"Latin",
                   "example":["export","import","transport","portable","porter","portal","portcullis"]},
          "ex-": {"meaning":"out, out of, away from","class":"prefix","root":"ex-",
                  "example":["export","exceed"]},
          "im-1": {"meaning":"in, into, on","class":"prefix","root":"im-1",
                   "example":["import","immerse"]},
          "tra-, trans-": {"meaning":"across, over","class":"prefix","root":"tra-, trans-",
                            "example":["transport"]},
          "-able, -ible, -ble": {"meaning":"capable of being","class":"adjective-forming suffix",
                                  "root":"-able, -ible, -ble","example":["portable","audible"]},
          "-er, -or, -ar2": {"meaning":"a person or thing that does","class":"noun-forming suffix",
                              "root":"-er, -or, -ar2","example":["porter","reporter"]}
        }
    """.trimIndent()

    private fun roots(): Roots = Roots.build(JSONObject(sample))

    @Test
    fun `word maps to its root`() {
        val r = roots()
        val port = r.rootOf("export")
        assertNotNull(port)
        assertEquals("port", port!!.root)
        assertEquals("carry", port.meaning)
        assertEquals("Latin", port.origin)
        assertEquals(7, port.family.size)
    }

    @Test
    fun `root lookup is case insensitive and misses on unknown`() {
        val r = roots()
        assertNotNull(r.rootOf("EXPORT"))
        assertEquals("port", r.rootOf("transport")!!.root)
        assertNull(r.rootOf("project"))
    }

    @Test
    fun `derives sourced breakdown with prefix and root`() {
        val bd = roots().breakdownOf("export")
        assertNotNull(bd)
        assertEquals(
            listOf(
                Roots.AffixPart("ex-", "前缀", "out, out of, away from"),
                Roots.AffixPart("port", "词根", "carry"),
            ),
            bd,
        )
    }

    @Test
    fun `derives breakdown with root and suffix for inflected forms`() {
        val bd = roots().breakdownOf("portable")
        assertNotNull(bd)
        assertEquals("port", bd!![0].part)
        assertEquals("-able", bd[1].part)
    }

    @Test
    fun `no fabricated breakdown when dataset does not cross list the word`() {
        val r = roots()
        assertNull(r.breakdownOf("portal"))
        assertNull(r.breakdownOf("portcullis"))
    }

    @Test
    fun `all roots sorted by family size descending`() {
        val r = roots()
        val all = r.allRoots()
        assertEquals(1, all.size)
        assertEquals("port", all[0].root)
        assertEquals(7, all[0].family.size)
    }
}

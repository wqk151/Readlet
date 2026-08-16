package com.readlet.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordLevelsTest {

    private val levels = WordLevels(
        mapOf(
            "trot" to WordLevels.Entry("六级", "/trɑt/", "n. 慢跑"),
            "hurtle" to WordLevels.Entry("专八", "/'hɝtl/", "vi. 猛冲"),
            "sweep" to WordLevels.Entry("专四", "/swip/", "v. 扫除", base = true),
            "cry" to WordLevels.Entry("六级", "/kraɪ/", "v. 哭"),
            "box" to WordLevels.Entry("考研", "/bɑks/", "n. 盒子", base = true),
            "knock" to WordLevels.Entry("雅思", "/nɑk/", "v. 敲"),
        )
    )

    // ---------- 变形还原 ----------

    @Test
    fun `forms strips ing`() {
        assertEquals(listOf("sweep", "sweepe"), WordLevels.forms("sweeping"))
    }

    @Test
    fun `forms strips ing with doubled consonant`() {
        assertEquals(listOf("trott", "trotte", "trot"), WordLevels.forms("trotting"))
    }

    @Test
    fun `forms strips ed and adds e`() {
        assertEquals(listOf("hurtl", "hurtle"), WordLevels.forms("hurtled"))
    }

    @Test
    fun `forms ies to y`() {
        assertEquals(listOf("cry"), WordLevels.forms("cries"))
    }

    @Test
    fun `forms es strip or add e`() {
        assertEquals(listOf("box", "boxe"), WordLevels.forms("boxes"))
    }

    @Test
    fun `forms strips plain s`() {
        assertEquals(listOf("knock"), WordLevels.forms("knocks"))
    }

    // ---------- 查词 ----------

    @Test
    fun `lookup exact match`() {
        assertEquals("六级", levels.lookup("trot")!!.level)
    }

    @Test
    fun `lookup inflected form resolves to base`() {
        assertEquals("专四", levels.lookup("sweeping")!!.level)
        assertEquals("专八", levels.lookup("hurtled")!!.level)
        assertEquals("六级", levels.lookup("cries")!!.level)
        assertEquals("考研", levels.lookup("boxes")!!.level)
        assertEquals("雅思", levels.lookup("knocked")!!.level)
    }

    @Test
    fun `lookup is case insensitive`() {
        assertEquals("六级", levels.lookup("TROT")!!.level)
    }

    @Test
    fun `lookup miss returns null`() {
        assertNull(levels.lookup("xyzzy"))
        assertNull(levels.lookup(""))
    }

    @Test
    fun `base flag preserved through inflection`() {
        val e = levels.lookup("sweeping")!!
        assertTrue(e.base)
        assertTrue(levels.lookup("boxes")!!.base)
        assertFalse(levels.lookup("hurtled")!!.base)
    }

    // ---------- 原型还原 ----------

    @Test
    fun `lemma resolves inflected form to base`() {
        assertEquals("sweep", levels.lemma("sweeping"))
        assertEquals("hurtle", levels.lemma("hurtled"))
        assertEquals("cry", levels.lemma("cries"))
        assertEquals("box", levels.lemma("boxes"))
        assertEquals("knock", levels.lemma("knocks"))
    }

    @Test
    fun `lemma returns null for base form and miss`() {
        assertNull(levels.lemma("trot"))
        assertNull(levels.lemma("loomed"))
        assertNull(levels.lemma(""))
    }
}

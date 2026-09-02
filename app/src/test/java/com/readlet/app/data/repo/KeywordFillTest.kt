package com.readlet.app.data.repo

import com.readlet.app.data.WordLevels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [KeywordFill] 级别补缺筛选回归测试：模拟 word_levels.tsv 关键行（ingredients 无级别遮蔽 / rebellion 四级标志）。 */
class KeywordFillTest {

    private fun levels(rebellionBase: Boolean = false) = WordLevels(
        mapOf(
            "ingredient" to WordLevels.Entry("雅思", "/ɪnˈɡriːdiənt/", "(混合物的)组成部分，配料"),
            "ingredients" to WordLevels.Entry("", "/ɪnˈɡriːdiənts/", "n. 材料；佐料"),
            "rebellion" to WordLevels.Entry("考研", "/rɪˈbeljən/", "n. 叛乱， 反抗， 起义", base = rebellionBase),
            "revolution" to WordLevels.Entry("考研", "/revəˈluːʃ(ə)n/", "n. 革命", base = true),
            "canyon" to WordLevels.Entry("六级", "/ˈkænjən/", "n. 峡谷"),
            "empire" to WordLevels.Entry("六级", "/ˈempaɪə/", "n. 帝国"),
            "feel" to WordLevels.Entry("雅思", "/fiːl/", "感觉", base = true),
            "felt" to WordLevels.Entry("专八", "/felt/", "n. 毡"),
            "were" to WordLevels.Entry("专四", "/wə/", "v. 是"),
            "harry" to WordLevels.Entry("考研", "/ˈhærɪ/", "n. 哈里"),
        )
    )

    private fun select(
        text: String,
        covered: List<String> = emptyList(),
        rebellionBase: Boolean = false,
        cap: Int = 10,
    ): List<String> = KeywordFill.select(text, covered, levels(rebellionBase), cap).map { it.word }

    // ---------- 回归：无级别词条遮蔽（原 bug：ingredients 有词条但无级别 → 原形雅思漏补） ----------

    @Test
    fun `no-level surface falls back to leveled base`() {
        assertEquals(listOf("ingredients"), select("The ingredients were fresh."))
    }

    @Test
    fun `surface meaning is exposed for the matched form`() {
        val c = KeywordFill.select("The ingredients were fresh.", emptyList(), levels(), 10).single()
        assertEquals("雅思", c.entry.level)
        assertEquals("n. 材料；佐料", c.surface!!.meaning)
    }

    // ---------- 回归：rebellions（词表无此形，规则还原 rebellion） ----------

    @Test
    fun `missing surface resolves via rule inflection`() {
        assertEquals(listOf("rebellions"), select("The rebellions failed."))
    }

    @Test
    fun `four-level base flag still blocks fill`() {
        // rebellion 词表原带四级基础标志（v7.2 已复核置 0）；模拟误标场景 → 不补
        assertTrue(select("The rebellions failed.", rebellionBase = true).isEmpty())
        // revolution 仍带四级基础标志（考研级但属基础词）→ 不补（防刷屏不变）
        assertTrue(select("The revolution began.").isEmpty())
    }

    // ---------- 原有守门规则不变 ----------

    @Test
    fun `homograph of base skipped`() {
        // sweeping/felt 词表单列专八词义、原形是四级基础词 → 跳过（防带入错义）
        assertTrue(select("The sweeping reforms felt odd.").isEmpty())
    }

    @Test
    fun `skips function words and capitalized names`() {
        // were 专四功能词、Harry 大写专有名词 → 均不补
        assertTrue(select("Were Harry mixing ingredients?", covered = listOf("ingredients")).isEmpty())
    }

    @Test
    fun `covered keyword also excludes inflected surface`() {
        // LLM 已返回 rebellion → 句中 rebellions 不再补
        assertTrue(select("The rebellions grew.", covered = listOf("rebellion")).isEmpty())
    }

    @Test
    fun `ignores words absent from table`() {
        assertTrue(select("wobble quaintly glimmer").isEmpty())
    }

    @Test
    fun `caps output and keeps sentence order`() {
        val text = "ingredient canyon empire"
        assertEquals(listOf("ingredient", "canyon"), select(text, cap = 2))
        assertEquals(listOf("ingredient", "canyon", "empire"), select(text))
    }
}

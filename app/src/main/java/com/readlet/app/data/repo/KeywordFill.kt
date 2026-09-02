package com.readlet.app.data.repo

import com.readlet.app.data.WordLevels
import com.readlet.app.ui.Keywords

/** 句中分词（字母+撇号），级别词补缺用。 */
private val TOKEN = Regex("[A-Za-z']+")

/**
 * 重点词级别补缺（纯 Kotlin，可单测）：句子分词 → 级别词表查缺补漏
 * （六级/考研/雅思/专四/专八），补 LLM 关键词漏挑的考试词。
 *
 * 跳过：功能词、首字母大写词（多为专有名词，柯林斯会误标级别）、已被 LLM 关键词覆盖的词
 * （含子串包含）、四级基础词（词表会误标 were/even/air 这类词，补了只会刷满高亮）、
 * 同形异义变形（felt→feel 类，词表级别属于另一词义）。
 * surface 词条无级别（柯林斯全量兜底词条，仅音标/释义）或未收录时，回退变形原形找带级别词条
 * （ingredients 无级别词条遮蔽 → ingredient 雅思），避免漏补。
 * 按句内顺序先到先得，最多 [cap] 个（防止整句刷角标）。
 */
object KeywordFill {

    /** 补缺候选：句中 surface 词形 + 级别词条（surface 本身或变形原形）+ surface 词条（可空，释义优先用）。 */
    data class Candidate(
        val word: String,
        val entry: WordLevels.Entry,
        val surface: WordLevels.Entry?,
    )

    /**
     * @param covered 已被 LLM 关键词覆盖的词（小写），补缺跳过。
     */
    fun select(
        text: String,
        covered: List<String>,
        wordLevels: WordLevels,
        cap: Int,
    ): List<Candidate> {
        val out = ArrayList<Candidate>()
        val processed = HashSet<String>()
        for (t in TOKEN.findAll(text)) {
            if (out.size >= cap) break
            val tl = t.value.lowercase()
            if (!processed.add(tl)) continue
            if (Keywords.isFunctionWord(tl)) continue
            // 首字母大写多为专有名词（Harry/McGonagall），柯林斯会误标级别，跳过
            if (t.value[0].isUpperCase()) continue
            if (covered.any { it == tl || it.contains(tl) || tl.contains(it) }) continue
            val surface = wordLevels.lookup(tl)
            val entry = wordLevels.leveledEntryOf(tl) ?: continue
            if (entry.base) continue
            // 同形异义变形（felt→feel）：词表把 felt 标成专八「毡」，句中却是基础词 feel
            // 的过去式；补缺只该加超纲词，这类词的超纲级别属于另一词义，补了只会带出错义，跳过。
            if (wordLevels.homographOfBase(tl)) continue
            out.add(Candidate(word = t.value, entry = entry, surface = surface))
        }
        return out
    }
}

package com.readlet.app.ui

/**
 * 词级搜索匹配（纯 Kotlin，可单测）。
 *
 * 不做跨词子序列匹配（旧 fzf 式实现会让 `couple` 命中一整段里散布 c/o/u/p/l/e 的任意长句）。
 * 规则：
 * - 单查询词：对句中每个词打分 —— 整词=100 / 词首=60 / 词内包含=30，取最高；0 分不命中。
 * - 词组查询（含空格）：按词序列精确匹配，命中=100。
 * - 大小写不敏感；命中区间为整个词/词组，直接用于高亮。
 */
object SearchMatcher {

    private val WORD = Regex("[A-Za-z']+")

    /** 匹配结果：分数 + 命中区间（用于句中高亮）。 */
    data class Result(val score: Int, val ranges: List<IntRange>)

    /** 匹配查询与文本；不命中返回 null。 */
    fun match(query: String, text: String): Result? {
        val q = query.trim().trim(' ', '.', ',', '!', '?', ':', ';', '"', '\'', '“', '”', '‘', '’', '「', '」', '(', ')', '[', ']').lowercase()
        if (q.isEmpty()) return null
        val tokens = WORD.findAll(text).map { it.range to it.value.lowercase() }.toList()
        val qWords = q.split(' ')
        return if (qWords.size > 1) phraseMatch(qWords, tokens) else wordMatch(q, tokens)
    }

    /** 匹配分；不命中返回 0（排序用）。 */
    fun score(query: String, text: String): Int = match(query, text)?.score ?: 0

    private fun wordMatch(q: String, tokens: List<Pair<IntRange, String>>): Result? {
        var bestScore = 0
        var bestRange: IntRange? = null
        for ((range, w) in tokens) {
            val s = when {
                w == q -> 100
                w.startsWith(q) -> 60
                w.contains(q) -> 30
                else -> 0
            }
            if (s > bestScore) {
                bestScore = s
                bestRange = range
            }
        }
        return bestRange?.let { Result(bestScore, listOf(it)) }
    }

    private fun phraseMatch(qWords: List<String>, tokens: List<Pair<IntRange, String>>): Result? {
        if (tokens.size < qWords.size) return null
        for (i in 0..tokens.size - qWords.size) {
            var ok = true
            for (k in qWords.indices) {
                if (tokens[i + k].second != qWords[k]) {
                    ok = false
                    break
                }
            }
            if (ok) {
                val range = tokens[i].first.first..tokens[i + qWords.size - 1].first.last
                return Result(100, listOf(range))
            }
        }
        return null
    }
}

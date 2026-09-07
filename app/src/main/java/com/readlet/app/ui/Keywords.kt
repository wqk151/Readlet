package com.readlet.app.ui

/**
 * 重点词文本处理（纯 Kotlin，可单测）。
 *
 * LLM 偶发把并列结构合并成一个关键词返回（如 `bottle fame, brew glory, stopper death`），
 * 导致：统计榜上一词多义粘连、点进去按整串匹配不到句中原文。
 * 拆分为独立词项后各自可匹配、可高亮。
 */
object Keywords {

    private val SPLIT = Regex("[,，、;；/]")
    private val TRAIL = Regex("[.!?…\"'“”‘’()\\[\\]{}]+$")

    /** 拆分单个关键词：`bottle fame, brew glory` → [bottle fame, brew glory]。丢弃空项与单字符。 */
    fun splitKeyword(raw: String): List<String> {
        val w = raw.trim()
        if (w.isEmpty()) return emptyList()
        return SPLIT.split(w).mapNotNull { part ->
            val p = TRAIL.replace(part.trim(), "").trim()
            p.takeIf { it.length >= 2 }
        }
    }

    /** 卡内合并：对每个 LLM 关键词拆分 + 大小写不敏感去重，保持顺序。 */
    fun splitKeywords(words: List<String>): List<String> {
        val seen = HashSet<String>()
        val out = ArrayList<String>()
        for (w in words) {
            for (p in splitKeyword(w)) {
                if (seen.add(p.lowercase())) out.add(p)
            }
        }
        return out
    }

    /**
     * 功能词黑名单（代词/be 动词/助动词/介词/连词/冠词等）：
     * 级别词补缺时跳过——词表可能给这些词打级别（如柯林斯把 were 标 TEM4），
     * 补成重点词没有学习价值，只会刷满高亮。
     */
    private val FUNCTION_WORDS = setOf(
        "a", "an", "the", "and", "or", "but", "nor", "so", "for", "yet",
        "of", "to", "in", "on", "at", "by", "with", "from", "up", "down",
        "out", "off", "over", "under", "into", "onto", "upon", "about",
        "after", "before", "between", "through", "during", "without",
        "against", "among", "towards", "across", "along", "behind", "beyond",
        "near", "past", "since", "until", "as", "than", "if", "then", "else",
        "when", "where", "why", "how", "what", "which", "who", "whom",
        "whose", "that", "this", "these", "those", "there", "here", "it",
        "its", "i", "you", "he", "she", "we", "they", "me", "him", "her",
        "us", "them", "my", "your", "his", "their", "our", "mine", "yours",
        "hers", "ours", "theirs", "is", "am", "are", "was", "were", "be",
        "been", "being", "have", "has", "had", "having", "do", "does", "did",
        "done", "will", "would", "shall", "should", "can", "could", "may",
        "might", "must", "not", "no", "yes", "all", "any", "some", "each",
        "every", "both", "either", "neither", "few", "many", "much", "more",
        "most", "little", "less", "least", "other", "another", "such", "own",
        "same", "very", "just", "only", "even", "still", "also", "too",
        "again", "once", "never", "always", "often", "sometimes", "usually",
        "well", "really", "quite", "rather", "almost", "nearly", "already",
    )

    /** 是否为功能词（级别补缺跳过用）。 */
    fun isFunctionWord(token: String): Boolean = token.lowercase() in FUNCTION_WORDS

    /** 级别显示标记映射：六级→CET6、专四→TEM4、专八→TEM8、雅思→IELTS；考研等无缩写级别原样显示。 */
    private val LEVEL_LABELS = mapOf(
        "六级" to "CET6",
        "四级" to "CET4",
        "专四" to "TEM4",
        "专八" to "TEM8",
        "雅思" to "IELTS",
    )

    /** 显示用级别标记；无级别（null/空白）返回 null 表示不显示。未知级别原样兜底。 */
    fun levelLabel(level: String?): String? {
        val l = level?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return LEVEL_LABELS[l] ?: l
    }

    /** 词根来源显示映射：Latin → 「Latin（拉丁语）」；未收录语种原样兜底（词根页/词根库共用）。 */
    private val ORIGIN_LABELS = mapOf(
        "Latin" to "Latin（拉丁语）",
        "Greek" to "Greek（希腊语）",
        "Latin and Greek" to "Latin and Greek（拉丁语和希腊语）",
        "Old English" to "Old English（古英语）",
        "Middle English" to "Middle English（中古英语）",
    )

    /** 词根来源展示串；来源为空返回 null 表示不显示。 */
    fun originLabel(origin: String?): String? {
        val o = origin?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return ORIGIN_LABELS[o] ?: o
    }

    /**
     * 词表释义词性前缀拆分：「n. 峡谷」→ (n., 峡谷)；「vt., vi. = brutalize」→ (vt., vi., = brutalize)。
     * 词表（柯林斯缓存）释义自带词性前缀，补缺词没有独立 pos 字段，直接展示会让「n.」出现在翻译前。
     * 拆出的词性只做前缀展示；多词性混排条目（如「n. 银行 v. 存款」）只拆开头词性，剩余保留原文。
     */
    private val POS_PREFIX = Regex(
        "^((?:(?:n|v|vi|vt|adj|adv|prep|conj|art|pron|num|int|aux|modal|abbr|vbl|pref|suf|pl|interj|a)" +
            "\\.(?:\\s*[,，]\\s*)?)+)\\s*(.*)$"
    )

    /** 拆词性前缀：返回 (词性, 释义)；无前缀返回 (null, 原文)。 */
    fun splitPos(meaning: String): Pair<String?, String> {
        if (meaning.isBlank()) return null to meaning
        val m = POS_PREFIX.matchEntire(meaning) ?: return null to meaning
        return m.groupValues[1].trimEnd() to m.groupValues[2].trim()
    }
}

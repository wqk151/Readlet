package com.readlet.app.data

import android.content.Context
import org.json.JSONObject
import java.io.IOException

/**
 * 词根词源数据（词根页/词根库/词源入口）。
 * 数据来自 `assets/wordroot.txt`（ECDICT `wordroot.txt`，MIT）：预先整理的英文词根/前后缀，
 * 772 条（class=root 503、prefix 117、各种 *-forming suffix 152）。每条含 meaning/meaningZh/class/root/origin/example[]。
 * 2026-09 起以蒋争《英语词汇的奥秘》（自购正版，个人使用）词根/词缀章节补充新词根与词族例词、
 * 以及 213 条词根的中文构词义（meaningZh，展示优先于英文）
 * （仅事实字段：词根拼写/构词义/例词；例词经 `word_levels.tsv` 收词校验、跳过既有归属词）。
 *
 * 启动时构建三层索引：
 * - [Roots.roots]：root → [RootEntry]（root/meaning/origin/family=example[]），供词根页与词根库；
 * - [Roots.wordToRoot]：词 → 出处词根（反向遍历 example[]），供「词源入口」判定；
 * - [Roots.breakdown]：词根词 → 推导拆解（前缀+词根+后缀），仅当该词同时被数据集的某前缀/后缀条目
 *   交叉收录、且归一化后拼接精确等于该词时才给出——绝不对数据集没承认的词编造拆解（无把握则 null）。
 *
 * 词根库只收录 class=root 条目（Q4=A：前后缀只在拆解里作构件，不单列）。
 */
class Roots internal constructor(
    private val roots: Map<String, RootEntry>,
    private val wordToRoot: Map<String, String>,
    private val breakdown: Map<String, List<AffixPart>>,
) {
    /** 词根条目：root=词根名，meaning=构词含义，meaningZh=中文构词义（蒋争数据源收录时才有，展示优先于英文），origin=来源（拉丁/希腊/古英），family=同根词族。 */
    data class RootEntry(
        val root: String,
        val meaning: String,
        val meaningZh: String? = null,
        val origin: String?,
        val family: List<String>,
    )

    /** 词根词缀构件：part=构件本身（ex- / port / -ing），type=前缀/词根/后缀，meaning=构件含义（记忆点）。 */
    data class AffixPart(val part: String, val type: String, val meaning: String)

    /** 全部词根（class=root），按词族规模降序（高产生词根优先），供词根库列表。 */
    fun allRoots(): List<RootEntry> = roots.values.sortedByDescending { it.family.size }

    /** 词 → 出处词根。未命中返回 null（不显示词源入口）。 */
    fun rootOf(word: String): RootEntry? {
        val low = word.trim().lowercase()
        return wordToRoot[low]?.let { roots[it] }
    }

    /** 词根名 → 词根条目；未收录返回 null。 */
    fun byRoot(name: String): RootEntry? = roots[name]

    /** 词根词的推导拆解；无把握返回 null（同根、不虚构）。 */
    fun breakdownOf(word: String): List<AffixPart>? = breakdown[word.trim().lowercase()]

    companion object {
        /**
         * 从资产加载（进程启动一次）。资产缺失时返回空表（不应发生，缺则词源功能静默失效）。
         */
        fun load(context: Context): Roots = try {
            val text = context.assets.open("wordroot.txt")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            build(JSONObject(text))
        } catch (_: IOException) {
            Roots(emptyMap(), emptyMap(), emptyMap())
        }

        /**
         * 解析 wordroot.json 并构建索引；纯函数（org.json），供单元测试直接调用。
         */
        internal fun build(json: JSONObject): Roots {
            val roots = HashMap<String, RootEntry>()
            // word → [(entryKey, meaning)]：为推导拆解预建前缀/后缀的词倒排。
            val wordPrefix = HashMap<String, MutableList<Pair<String, String>>>()
            val wordSuffix = HashMap<String, MutableList<Pair<String, String>>>()

            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val o = json.optJSONObject(key) ?: continue
                val cls = o.optString("class")
                val example = o.optJSONArray("example")
                val family = ArrayList<String>(example?.length() ?: 0)
                example?.let { arr -> for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let { family.add(it) } }
                when {
                    cls == "root" -> {
                        val canonical = o.optString("root").takeIf { it.isNotBlank() } ?: key
                        roots[canonical] = RootEntry(
                            root = canonical,
                            meaning = o.optString("meaning"),
                            meaningZh = o.optString("meaningZh").takeIf { it.isNotBlank() },
                            origin = o.optString("origin").takeIf { it.isNotBlank() },
                            family = family,
                        )
                    }
                    cls == "prefix" -> for (w in family) wordPrefix.getOrPut(w.lowercase()) { ArrayList() }.add(key to o.optString("meaning"))
                    cls.contains("suffix") -> for (w in family) wordSuffix.getOrPut(w.lowercase()) { ArrayList() }.add(key to o.optString("meaning"))
                }
            }

            val wordToRoot = HashMap<String, String>()
            val breakdown = HashMap<String, List<AffixPart>>()
            for ((canonical, entry) in roots) {
                for (word in entry.family) {
                    val low = word.lowercase()
                    wordToRoot.putIfAbsent(low, canonical)
                }
                val rootVariants = variants(canonical)
                for (word in entry.family) {
                    val low = word.lowercase()
                    deriveBreakdown(word, entry, rootVariants, wordPrefix[low].orEmpty(), wordSuffix[low].orEmpty())
                        ?.let { breakdown[low] = it }
                }
            }
            return Roots(roots, wordToRoot, breakdown)
        }

        /**
         * 推导 word 的拆解 [前缀+词根+后缀]。
         * 仅当 word 同时被数据集某前缀条目和/或某后缀条目交叉收录，且
         * `prefixVariant + rootVariant + suffixVariant`（归一化后）精确等于 word 才返回；
         * 否则返回 null（同根、不虚构）。多候选时取"前后缀都命中且最长"者，最完整。
         */
        private fun deriveBreakdown(
            word: String,
            rootEntry: RootEntry,
            rootVariants: List<String>,
            prefixes: List<Pair<String, String>>,
            suffixes: List<Pair<String, String>>,
        ): List<AffixPart>? {
            var best: List<AffixPart>? = null
            for ((pKey, pMeaning) in prefixes) {
                for (pBase in variants(pKey)) {
                    for (rv in rootVariants) {
                        // 前缀 + 词根（无后缀）
                        if (pBase + rv == word) best = bestOf(
                            best,
                            listOf(AffixPart("$pBase-", "前缀", pMeaning), AffixPart(rv, "词根", rootEntry.meaning)),
                        )
                        for ((sKey, sMeaning) in suffixes) {
                            for (sBase in variants(sKey)) {
                                // 前缀 + 词根 + 后缀
                                if (pBase + rv + sBase == word) best = bestOf(
                                    best,
                                    listOf(
                                        AffixPart("$pBase-", "前缀", pMeaning),
                                        AffixPart(rv, "词根", rootEntry.meaning),
                                        AffixPart("-$sBase", "后缀", sMeaning),
                                    ),
                                )
                            }
                        }
                    }
                }
            }
            for ((sKey, sMeaning) in suffixes) {
                for (sBase in variants(sKey)) {
                    for (rv in rootVariants) {
                        // 词根 + 后缀（无前缀）
                        if (rv + sBase == word) best = bestOf(
                            best,
                            listOf(AffixPart(rv, "词根", rootEntry.meaning), AffixPart("-$sBase", "后缀", sMeaning)),
                        )
                    }
                }
            }
            return best
        }

        /** 多候选取更完整者：三构件（前缀+词根+后缀）优先于两构件。 */
        private fun bestOf(current: List<AffixPart>?, candidate: List<AffixPart>): List<AffixPart> =
            if (current == null || candidate.size > current.size) candidate else current

        /**
         * 条目名拆成可拼接的变体：按逗号/斜杠/空格切分 → 去边缘连字符 → 去尾部消歧数字。
         * 例：`ex-`→`ex`；`im-1`→`im`；`-able, -ible, -ble`→`[able,ible,ble]`；`pter, ptero, pteryg, pteryx`→多值。
         */
        private fun variants(raw: String): List<String> {
            val out = ArrayList<String>()
            for (part in raw.split(',', '/', ' ', '；', '，')) {
                val base = part.trim().trim('-').trim()
                if (base.isEmpty()) continue
                val deNum = base.trimEnd { it.isDigit() }.trimEnd('-').trim()
                if (deNum.isNotEmpty()) out.add(deNum)
            }
            return out
        }
    }
}

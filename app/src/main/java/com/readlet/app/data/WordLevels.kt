package com.readlet.app.data

import android.content.Context
import java.io.IOException

/**
 * 重点词级别表：词 → (级别, 音标, 释义)。
 * 数据来自 `assets/word_levels.tsv`（16,241 词，源：本地柯林斯缓存 + 开源考研/雅思词表）。
 * 启动时加载为 HashMap，查词 O(1)；查词带变形还原回退（sweeping→sweep、trotting→trot、cries→cry）。
 */
class WordLevels internal constructor(
    private val map: Map<String, Entry>,
) {
    /** 词条：级别（六级/考研/雅思/专四/专八）+ 音标（可空）+ 释义（可空）+ 是否四级基础词。 */
    data class Entry(val level: String, val phonetic: String, val meaning: String, val base: Boolean = false)

    /** 查词（含变形还原回退）；未命中返回 null。 */
    fun lookup(token: String): Entry? {
        val w = token.trim().lowercase()
        if (w.isEmpty()) return null
        map[w]?.let { return it }
        for (baseForm in forms(w)) {
            map[baseForm]?.let { return it }
        }
        return null
    }

    /**
     * 词的原形（表内规范词形）：`loomed` → `loom`、`cries` → `cry`。
     * 本身是原形或未命中返回 null。与 [lookup] 同一变形还原规则；
     * 不规则变形（knelt→kneel）不在表内覆盖范围，返回 null。
     */
    fun lemma(token: String): String? {
        val w = token.trim().lowercase()
        if (w.isEmpty() || map.containsKey(w)) return null
        for (baseForm in forms(w)) {
            if (map.containsKey(baseForm)) return baseForm
        }
        return null
    }

    companion object {
        /** 从资产加载（进程启动一次）。资产缺失时返回空表（不应发生）。 */
        fun load(context: Context): WordLevels {
            val map = HashMap<String, Entry>()
            try {
                context.assets.open("word_levels.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
                    for (line in lines) {
                        val p = line.split('\t')
                        if (p.size >= 2 && p[0].isNotEmpty()) {
                            map[p[0]] = Entry(
                                level = p[1],
                                // 柯林斯方括号音标 [luːm] 归一为 /luːm/，与 LLM 输出格式一致
                                phonetic = p.getOrElse(2) { "" }.trim().let { raw ->
                                    if (raw.startsWith("[") && raw.endsWith("]") && raw.length > 2)
                                        "/${raw.substring(1, raw.length - 1).trim()}/"
                                    else raw
                                },
                                meaning = p.getOrElse(3) { "" },
                                base = p.getOrElse(4) { "0" } == "1",
                            )
                        }
                    }
                }
            } catch (_: IOException) {
                // 资产缺失：级别标记静默失效，不影响其余功能
            }
            return WordLevels(map)
        }

        /**
         * 变形候选（原形优先，不含原词）：
         * - ing → strip / +e / 双写去一（trotting→trot）
         * - ed → strip / +e / 双写去一（hurtled→hurtle）
         * - ies → y（cries→cry）；es → strip / +e；s → strip
         */
        fun forms(w: String): List<String> {
            val out = ArrayList<String>(6)
            fun add(s: String) {
                if (s.length >= 2 && s != w && !out.contains(s)) out.add(s)
            }
            when {
                w.endsWith("ing") -> {
                    val b = w.dropLast(3)
                    add(b)
                    add(b + "e")
                    if (b.length >= 2 && b.last() == b[b.length - 2]) add(b.dropLast(1))
                }
                w.endsWith("ied") -> add(w.dropLast(3) + "y")
                w.endsWith("ed") -> {
                    val b = w.dropLast(2)
                    add(b)
                    add(b + "e")
                    if (b.length >= 2 && b.last() == b[b.length - 2]) add(b.dropLast(1))
                }
                w.endsWith("ies") -> add(w.dropLast(3) + "y")
                w.endsWith("es") -> {
                    add(w.dropLast(2))
                    add(w.dropLast(2) + "e")
                }
                w.endsWith("s") -> add(w.dropLast(1))
            }
            return out
        }
    }
}

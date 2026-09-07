package com.readlet.app.llm

import org.json.JSONArray
import org.json.JSONObject

/** 词源分析产物：给定词根 → 词族每词的构词拆解 + 推导义（LLM 生成，持久化于 root_etymology）。 */
data class EtymologyResult(
    val root: String,
    val meaning: String?,
    val origin: String?,
    val items: List<EtymologyItem>,
)

/** 词族词条目。breakdown 为 null 表示无法可靠拆解（诚实缺省）；meaning 为推导义或中文释义。 */
data class EtymologyItem(
    val word: String,
    val breakdown: List<EtymologyPart>?,
    val meaning: String?,
)

/** 构词构件：part=ex- / port / -ment，type=前缀/词根/后缀，meaning=简要中文义（向外/搬运/名词）。 */
data class EtymologyPart(
    val part: String,
    val type: String,
    val meaning: String,
)

/** 解析 LLM 词源 JSON 响应 → [EtymologyResult]（lenient：剥围栏、缺项兜底）。 */
object EtymologyParser {

    fun parse(raw: String): EtymologyResult {
        val cleaned = clean(raw)
        val obj = JSONObject(cleaned)
        val items = normalizeItems(parseItems(obj.optJSONArray("items")))
        return EtymologyResult(
            root = obj.optString("root"),
            meaning = obj.optStringOrNull("meaning"),
            origin = obj.optStringOrNull("origin"),
            items = items,
        )
    }

    /** 编码为 itemsJson 持久化（与 [decodeItems] 对称）。 */
    fun encodeItems(items: List<EtymologyItem>): String? {
        if (items.isEmpty()) return null
        val arr = JSONArray()
        for (item in items) {
            val o = JSONObject().put("word", item.word)
            if (item.breakdown == null) {
                o.put("breakdown", JSONObject.NULL)
            } else {
                val bd = JSONArray()
                for (p in item.breakdown) {
                    bd.put(JSONObject().put("part", p.part).put("type", p.type).put("meaning", p.meaning))
                }
                o.put("breakdown", bd)
            }
            o.put("meaning", item.meaning ?: JSONObject.NULL)
            arr.put(o)
        }
        return arr.toString()
    }

    fun decodeItems(itemsJson: String?): List<EtymologyItem> {
        if (itemsJson.isNullOrBlank()) return emptyList()
        val arr = JSONArray(itemsJson)
        val list = buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val word = o.optString("word").takeIf { it.isNotBlank() } ?: continue
                val bd = o.opt("breakdown")?.let { raw ->
                    if (raw == JSONObject.NULL) null else parseParts(raw as? JSONArray)
                }
                add(EtymologyItem(word, bd, o.optStringOrNull("meaning")))
            }
        }
        return normalizeItems(list)
    }

    /** 统一同一词族内相同构件的释义：同一 (part, type) 取出现最多的义；平手取词族中先出现的义。
     * 消除 LLM 给同一词缀跨词漂移（如 de- 被译成"离开/完全"不一致）的问题。 */
    internal fun normalizeItems(items: List<EtymologyItem>): List<EtymologyItem> {
        if (items.isEmpty()) return items
        val freq = LinkedHashMap<Pair<String, String>, LinkedHashMap<String, Int>>()
        for (item in items) {
            for (p in item.breakdown.orEmpty()) {
                if (p.part.isBlank()) continue
                val byKey = freq.getOrPut(p.part to p.type) { LinkedHashMap() }
                if (p.meaning.isNotBlank()) byKey[p.meaning] = (byKey[p.meaning] ?: 0) + 1
            }
        }
        val unified = HashMap<Pair<String, String>, String>()
        for ((key, byMeaning) in freq) {
            val best = byMeaning.entries.maxByOrNull { it.value }?.key ?: continue
            unified[key] = best
        }
        return items.map { item ->
            item.copy(breakdown = item.breakdown?.map { p ->
                val u = unified[p.part to p.type]
                if (u.isNullOrBlank()) p else p.copy(meaning = u)
            })
        }
    }

    private fun parseParts(arr: JSONArray?): List<EtymologyPart>? {
        if (arr == null || arr.length() == 0) return null
        val out = ArrayList<EtymologyPart>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i)
            val a = arr.optJSONArray(i)
            val part: String?
            var type = ""
            var meaning = ""
            if (obj != null) {
                part = obj.optString("part"); type = obj.optString("type"); meaning = obj.optString("meaning")
            } else if (a != null) {
                part = a.optString(0); type = a.optString(1); meaning = a.optString(2)
            } else continue
            if (part.isNullOrBlank()) continue
            out.add(EtymologyPart(part, type.takeIf { it.isNotBlank() } ?: "", meaning.takeIf { it.isNotBlank() } ?: ""))
        }
        return out.takeIf { it.isNotEmpty() }
    }

    private fun parseItems(arr: JSONArray?): List<EtymologyItem> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val word = item.optString("word").takeIf { it.isNotBlank() } ?: continue
                val bd = item.opt("breakdown")?.let { raw ->
                    if (raw == JSONObject.NULL) null else parseParts(raw as? JSONArray)
                }
                add(EtymologyItem(word, bd, item.optStringOrNull("meaning")))
            }
        }
    }

    private fun clean(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("```")) s = s.removePrefix("```").removePrefix("json").removePrefix("JSON").trim()
        if (s.endsWith("```")) s = s.removeSuffix("```").trim()
        return s
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
}

package com.readlet.app.llm

import org.json.JSONArray
import org.json.JSONObject

/** 把 LLM 原始文本解析为 [AnalyzeResult]。带 lenient 处理（剥围栏、缺字段兜底）。 */
object AnalysisParser {

    fun parse(raw: String): AnalyzeResult {
        val cleaned = clean(raw)
        val obj = JSONObject(cleaned)
        val mode = obj.optString("mode", "sentence")
        return AnalyzeResult(
            mode = mode,
            translation = obj.optStringOrNull("translation"),
            keywords = parseList(obj.optJSONArray("keywords")) { it ->
                // 旧 prompt 只回 phonetic（英式），映射到 phoneticUk 保持兼容。
                val rawUk = it.optStringOrNull("phonetic_uk") ?: it.optStringOrNull("phonetic")
                val (uk, us) = splitPhoneticField(rawUk)
                AnalyzeResult.Keyword(
                    word = it.getString("word"),
                    phoneticUk = uk,
                    // 显式 phonetic_us 优先；LLM 把「英 X，美 Y」合并进单字段时由拆分兜底。
                    phoneticUs = it.optStringOrNull("phonetic_us") ?: us,
                    pos = it.optStringOrNull("pos"),
                    level = it.optStringOrNull("level"),
                    lemma = it.optStringOrNull("lemma"),
                    meaningInContext = it.optStringOrNull("meaning_in_context"),
                )
            },
            points = parseList(obj.optJSONArray("points")) { it ->
                AnalyzeResult.Point(
                    expr = it.optStringOrNull("expr") ?: "",
                    meaning = it.optStringOrNull("meaning") ?: "",
                )
            },
            grammar = parseList(obj.optJSONArray("grammar")) { it ->
                AnalyzeResult.Grammar(
                    structure = it.optStringOrNull("structure") ?: "",
                    explanation = it.optStringOrNull("explanation") ?: "",
                )
            },
            collocations = parseList(obj.optJSONArray("collocations")) { it ->
                AnalyzeResult.Collocation(
                    phrase = it.getString("phrase"),
                    meaning = it.getString("meaning"),
                    example = it.optStringOrNull("example"),
                    exampleCn = it.optStringOrNull("example_cn"),
                )
            },
        )
    }

    private fun clean(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("```")) {
            s = s.removePrefix("```json").removePrefix("```JSON").removePrefix("```")
                .trim()
            if (s.endsWith("```")) s = s.dropLast(3).trim()
        }
        return s
    }

    private val IPA_TOKEN = Regex("/[^/]+/")
    private val UK_MARKED = Regex("(?:英(?:式)?(?:发音)?|UK)\\s*[：:]?\\s*(/[^/]+/)", RegexOption.IGNORE_CASE)
    private val US_MARKED = Regex("(?:美(?:式)?(?:发音)?|US)\\s*[：:]?\\s*(/[^/]+/)", RegexOption.IGNORE_CASE)

    /**
     * 归一化 LLM 音标字段：偶发把「英 /wɒnd/，美 /wɑːnd/」合并写进单个字段。
     * 返回 (英式, 美式)：
     * - 带英/美标记 → 按标记各自提取；
     * - 无标记的多段 IPA（如 "/x/ /y/"）→ 按顺序取前两段为英、美；
     * - 其余情况整串视为英式原样透传（旧数据兼容，不丢信息）。
     */
    internal fun splitPhoneticField(raw: String?): Pair<String?, String?> {
        if (raw.isNullOrBlank()) return null to null
        val uk = UK_MARKED.find(raw)?.groupValues?.get(1)
        val us = US_MARKED.find(raw)?.groupValues?.get(1)
        if (uk != null || us != null) return uk to us
        val tokens = IPA_TOKEN.findAll(raw).map { it.value }.toList()
        return when {
            tokens.size >= 2 -> tokens[0] to tokens[1]
            tokens.size == 1 -> tokens[0] to null
            else -> raw to null
        }
    }

    private inline fun <T> parseList(arr: JSONArray?, mapper: (JSONObject) -> T): List<T> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                try {
                    add(mapper(item))
                } catch (_: Exception) {
                    // 跳过单个坏条目，不整卡失败
                }
            }
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
}

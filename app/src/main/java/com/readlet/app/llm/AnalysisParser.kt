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
                AnalyzeResult.Keyword(
                    word = it.getString("word"),
                    phonetic = it.optStringOrNull("phonetic"),
                    pos = it.optStringOrNull("pos"),
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

package com.readlet.app.llm

/** LLM 分析的结构化结果，对应 assets/analyze_prompt.txt 第九节 JSON Schema。 */
data class AnalyzeResult(
    val mode: String,                 // sentence | word | phrase
    val translation: String?,
    val keywords: List<Keyword>,
    val points: List<Point>,
    val grammar: List<Grammar>,
    val collocations: List<Collocation>,
) {
    data class Keyword(
        val word: String,
        val phonetic: String?,
        val pos: String?,
        val meaningInContext: String?,
    )

    data class Point(val expr: String, val meaning: String)

    data class Grammar(val structure: String, val explanation: String)

    data class Collocation(
        val phrase: String,
        val meaning: String,
        val example: String?,
        val exampleCn: String?,
    )
}

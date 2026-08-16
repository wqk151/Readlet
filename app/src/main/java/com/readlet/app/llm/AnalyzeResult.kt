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
        val phoneticUk: String?,      // 英式音标（旧 prompt 的 phonetic 也归入此字段）
        val phoneticUs: String?,      // 美式音标
        val pos: String?,
        val level: String?,           // 考试级别：六级/考研/雅思/专四/专八
        val lemma: String?,           // 原型（动词原形/名词单数）；原形或短语为 null
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

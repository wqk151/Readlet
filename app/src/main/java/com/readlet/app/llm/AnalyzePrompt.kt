package com.readlet.app.llm

import android.content.Context
import java.io.IOException

/** 读取 prompt 资产并在请求时拼接输入（含可选术语表）。 */
object AnalyzePrompt {

    private const val ASSET = "analyze_prompt.txt"

    fun system(context: Context, glossary: String = ""): String {
        val base = try {
            context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: IOException) {
            // 资产缺失时用内置兜底说明（不应发生）
            "你是英语学习助手，面向中文母语学习者，分析输入的英文句子。"
        }
        val terms = parseGlossary(glossary)
        if (terms.isEmpty()) return base
        return base + "\n\n# 十、术语表（重要）\n" +
            "以下术语出现时，翻译必须使用指定译法，不得使用其他同义表达：\n" +
            terms.joinToString("\n")
    }

    /**
     * 解析术语表多行文本 → 规范化的 `term → 译法` 行。
     * 每行支持分隔符：→ / => / ： / = / :；丢弃空行与无分隔符行。
     * 纯函数，可单测。
     */
    fun parseGlossary(glossary: String): List<String> = glossary.lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val sep = listOf("→", "=>", "：", "=", ":").firstOrNull { line.contains(it) }
            if (sep == null) null
            else {
                val i = line.indexOf(sep)
                val term = line.substring(0, i).trim()
                val trans = line.substring(i + sep.length).trim()
                if (term.isNotEmpty() && trans.isNotEmpty()) "$term → $trans" else null
            }
        }
}

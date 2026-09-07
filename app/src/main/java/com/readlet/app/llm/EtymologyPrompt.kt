package com.readlet.app.llm

import android.content.Context
import java.io.IOException

/** 读取词源分析 prompt 资产并在请求时拼接输入（词根 + 构词义 + 来源 + 同根词族）。 */
object EtymologyPrompt {
    private const val ASSET = "analyze_etymology.txt"

    /** 词源 prompt 版本：变更时 bump，使旧缓存（promptVersion < VERSION）在读取时重新生成。 */
    const val VERSION = 1

    fun system(context: Context): String = try {
        context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
    } catch (_: IOException) {
        "你是英语词源词典编者，为给定词根分析其同根词的构词拆解与推导义，只输出一个合法 JSON 对象，禁止编造。"
    }

    /** 用户消息：词根 + 构词义 + 来源 + 同根词族（供 prompt 逐词分析）。 */
    fun request(root: String, meaning: String?, origin: String?, family: List<String>): String =
        "词根：$root（构词义：${meaning ?: "未知"}；来源：${origin ?: "未知"}）\n同根词族：${family.joinToString(", ")}"
}

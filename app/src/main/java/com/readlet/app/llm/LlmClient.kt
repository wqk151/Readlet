package com.readlet.app.llm

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 调用失败分类：网络类（可稍后重试） vs 解析类（不再自动重试）。 */
sealed class LlmException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Network(message: String, cause: Throwable? = null) : LlmException(message, cause)
    class BadResponse(message: String, cause: Throwable? = null) : LlmException(message, cause)
}

interface ChatApi {
    @POST("chat/completions")
    suspend fun chat(@Body body: RequestBody): Response<ResponseBody>
}

/** 分析响应：内容 + token 用量（部分服务商可能不返回 usage，缺省 0）。 */
data class LlmResponse(
    val content: String,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
)

/** DeepSeek（OpenAI 兼容协议）客户端。线程安全，可复用。 */
class LlmClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
) {
    private val api: ChatApi = Retrofit.Builder()
        .baseUrl(baseUrl.let { if (it.endsWith("/")) it else "$it/" })
        .client(
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                })
                // OpenAI 兼容协议认证：不带 Bearer 头会返回 401。
                // 客户端在设置变更时通过 refreshClient() 重建，key 始终是最新的。
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("Authorization", "Bearer $apiKey")
                            .build()
                    )
                }
                .build()
        )
        .build()
        .create(ChatApi::class.java)

    /**
     * 分析一段英文（句子/短语/单词）。返回内容与 token 用量，由 [AnalysisParser] 解析内容。
     * @throws LlmException
     */
    suspend fun analyze(systemPrompt: String, text: String): LlmResponse {
        val json = JSONObject()
            .put("model", model)
            .put("temperature", 0.3)
            // DeepSeek v4 思考模式默认开启（high effort）：结构化 JSON 分析任务收益低、
            // reasoning token 计费高、延迟翻倍，显式禁用（兼容端点一般忽略未知参数）。
            .put("thinking", JSONObject().put("type", "disabled"))
            .put("messages", org.json.JSONArray()
                .put(JSONObject().put("role", "system").put("content", systemPrompt))
                .put(JSONObject().put("role", "user").put("content", text))
            )
        val body: RequestBody = json.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val resp = try {
            api.chat(body)
        } catch (e: IOException) {
            throw LlmException.Network("网络不可用", e)
        } catch (e: Exception) {
            throw LlmException.Network("请求失败: ${e.message}", e)
        }

        if (!resp.isSuccessful) {
            throw LlmException.BadResponse("HTTP ${resp.code()}: ${resp.errorBody()?.string()?.take(200)}")
        }
        val content = resp.body()?.string() ?: throw LlmException.BadResponse("空响应体")
        val root = JSONObject(content)
        val message = root.optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?: throw LlmException.BadResponse("响应缺少 choices[0].message.content")
        if (message.isBlank()) throw LlmException.BadResponse("分析内容为空")
        val usage = root.optJSONObject("usage")
        return LlmResponse(
            content = message,
            promptTokens = usage?.optInt("prompt_tokens") ?: 0,
            completionTokens = usage?.optInt("completion_tokens") ?: 0,
        )
    }
}

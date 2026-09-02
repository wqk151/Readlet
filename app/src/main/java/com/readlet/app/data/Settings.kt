package com.readlet.app.data

import android.content.Context
import android.content.SharedPreferences
import com.readlet.app.tts.TtsIds

/** 设置项（SharedPreferences）。修改后返回是否实际变化。 */
class Settings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("readlet", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(v) = prefs.edit().putString(KEY_API_KEY, v).apply()

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(v) = prefs.edit().putString(KEY_BASE_URL, v).apply()

    var model: String
        get() = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(v) = prefs.edit().putString(KEY_MODEL, v).apply()

    /** 翻译术语表：多行文本，每行 `词 → 译法`（分隔符支持 → / = / ：）。分析时注入 prompt。 */
    var glossary: String
        get() = prefs.getString(KEY_GLOSSARY, "") ?: ""
        set(v) = prefs.edit().putString(KEY_GLOSSARY, v).apply()

    /** 语音引擎：系统语音（默认）或本地语音引擎（需下载语音包）。 */
    var voiceEngine: String
        get() = prefs.getString(KEY_VOICE_ENGINE, TtsIds.ENGINE_SYSTEM) ?: TtsIds.ENGINE_SYSTEM
        set(v) = prefs.edit().putString(KEY_VOICE_ENGINE, v).apply()

    /** 本地音色 id（VoiceCatalog.usVoices 的 id；引擎为系统语音时忽略）。 */
    var voiceId: String
        get() = prefs.getString(KEY_VOICE_ID, TtsIds.DEFAULT_LOCAL_VOICE) ?: TtsIds.DEFAULT_LOCAL_VOICE
        set(v) = prefs.edit().putString(KEY_VOICE_ID, v).apply()

    companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
        const val DEFAULT_MODEL = "deepseek-v4-flash"
        val MODELS = listOf("deepseek-v4-flash", "deepseek-v4-pro")
        private const val KEY_API_KEY = "api_key"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_MODEL = "model"
        private const val KEY_GLOSSARY = "glossary"
        private const val KEY_VOICE_ENGINE = "voice_engine"
        private const val KEY_VOICE_ID = "voice_id"
    }
}

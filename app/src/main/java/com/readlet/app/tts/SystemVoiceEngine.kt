package com.readlet.app.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 系统语音引擎：TextToSpeech + Locale.US（美音优先，实际音质取决于设备 ROM 与语音包）。 */
class SystemVoiceEngine(context: Context) {

    private val ready = CompletableFuture<Boolean>()
    private val tts = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            // 引擎语言在 speak 前按需设置；此处仅标记就绪。
            ready.complete(true)
        } else {
            ready.complete(false)
        }
    }

    /** 播报文本（打断式）。返回 false = 引擎不可用/初始化失败/合成失败。 */
    suspend fun speak(text: String): Boolean {
        if (text.isBlank()) return false
        // 首次初始化可能数百 ms：在 IO 线程阻塞等待（不卡主线程）。
        if (!withContext(Dispatchers.IO) { awaitReady() }) return false
        return withContext(Dispatchers.Main.immediate) {
            tts.language = Locale.US
            tts.stop()
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "readlet-tts") >= TextToSpeech.SUCCESS
        }
    }

    @Synchronized
    fun stop() {
        runCatching { tts.stop() }
    }

    /** 引擎不再使用（切引擎/进程退出）时释放系统资源。 */
    fun shutdown() {
        runCatching { tts.shutdown() }
    }

    /** CompletableFuture.get 为阻塞调用，仅允许在 IO 线程执行。 */
    private fun awaitReady(): Boolean {
        return try {
            ready.get(3, TimeUnit.SECONDS)
        } catch (_: Exception) {
            false
        }
    }
}

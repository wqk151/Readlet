package com.readlet.app.tts

import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 本地语音引擎：sherpa-onnx（AAR 1.13.7）+ kokoro int8 v1.1。
 * 构造即加载模型（阻塞 ~1s 级，须在后台线程）；generate 为 CPU 密集调用，
 * 由 TtsManager 的 speakMutex 保证串行。实例常驻进程（lazy 加载）。
 */
class SherpaKokoroEngine private constructor(private val tts: OfflineTts) {

    /**
     * 合成文本。@param sid 声线 id（VoiceCatalog.US_VOICES[i].sid）。
     * @return 24kHz 单声道 16-bit PCM；失败返回 null。
     */
    suspend fun synthesize(text: String, sid: Int): WavAudio? = withContext(Dispatchers.Default) {
        try {
            val audio = tts.generate(text, sid, 1.0f)
            val samples = audio.samples
            if (samples.isEmpty() || audio.sampleRate <= 0) return@withContext null
            WavAudio(audio.sampleRate, pcm16(samples))
        } catch (_: Exception) {
            null
        }
    }

    /** 释放（OfflineTts 无显式释放 API；native 内存随进程回收，此处为接口占位）。 */
    fun release() = Unit

    companion object {
        /** 加载模型；失败返回 null（模型文件缺失/损坏/不兼容）。须在后台线程调用。 */
        fun load(modelDir: File): SherpaKokoroEngine? = try {
            val kokoro = OfflineTtsKokoroModelConfig(
                model = File(modelDir, VoiceCatalog.MODEL_REL).absolutePath,
                voices = File(modelDir, VoiceCatalog.VOICES_REL).absolutePath,
                tokens = File(modelDir, VoiceCatalog.TOKENS_REL).absolutePath,
                dataDir = File(modelDir, VoiceCatalog.ESPEAK_DATA_REL).absolutePath,
                lexicon = File(modelDir, VoiceCatalog.LEXICON_REL).absolutePath,
            )
            val modelConfig = OfflineTtsModelConfig(
                kokoro = kokoro,
                numThreads = 4,
                debug = false,
                provider = "cpu",
            )
            val config = OfflineTtsConfig(model = modelConfig)
            SherpaKokoroEngine(OfflineTts(assetManager = null, config = config))
        } catch (_: Throwable) {
            null
        }
    }
}

/** Float 样本（[-1,1]）→ 16-bit PCM little-endian。 */
private fun pcm16(samples: FloatArray): ByteArray {
    val out = ByteArray(samples.size * 2)
    var i = 0
    for (s in samples) {
        val v = (s.coerceIn(-1f, 1f) * 32767f).roundToInt()
        out[i++] = (v and 0xff).toByte()
        out[i++] = ((v shr 8) and 0xff).toByte()
    }
    return out
}

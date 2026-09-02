package com.readlet.app.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.io.File

/** 16-bit PCM WAV 的解析结果。 */
data class WavAudio(val sampleRate: Int, val pcm: ByteArray) {
    /** 时长毫秒（供日志/调试）。 */
    val durationMs: Long
        get() = if (sampleRate <= 0) 0 else pcm.size * 1000L / (2 * sampleRate)
}

/** 解析单声道 16-bit PCM WAV（本应用合成产物）；格式不符返回 null。 */
fun readWav(file: File): WavAudio? {
    if (!file.isFile || file.length() < 44) return null
    return try {
        file.inputStream().use { ins ->
            val header = ByteArray(44)
            if (ins.read(header) != header.size) return null
            fun u16(offset: Int) = (header[offset].toInt() and 0xff) or
                ((header[offset + 1].toInt() and 0xff) shl 8)
            fun u32(offset: Int) = (header[offset].toInt() and 0xff) or
                ((header[offset + 1].toInt() and 0xff) shl 8) or
                ((header[offset + 2].toInt() and 0xff) shl 16) or
                ((header[offset + 3].toInt() and 0xff) shl 24)
            if (header[0] != 'R'.code.toByte() || header[1] != 'I'.code.toByte() ||
                header[2] != 'F'.code.toByte() || header[3] != 'F'.code.toByte()
            ) return null
            if (header[8] != 'W'.code.toByte() || header[9] != 'A'.code.toByte() ||
                header[10] != 'V'.code.toByte() || header[11] != 'E'.code.toByte()
            ) return null
            if (u16(20) != 1) return null // PCM
            val channels = u16(22)
            val sampleRate = u32(24)
            val bits = u16(34)
            if (channels != 1 || bits != 16 || sampleRate <= 0) return null
            // 找 data 块（不假设偏移 44 即 data，容忍扩展块）。
            var offset = 12
            var dataSize = -1
            while (offset + 8 <= header.size) {
                if (header[offset] == 'd'.code.toByte() && header[offset + 1] == 'a'.code.toByte() &&
                    header[offset + 2] == 't'.code.toByte() && header[offset + 3] == 'a'.code.toByte()
                ) {
                    dataSize = u32(offset + 4)
                    break
                }
                // 块大小（含本 8 字节头之外）→ 跨过
                val chunk = u32(offset + 4)
                offset += 8 + chunk
                if (offset > file.length()) return null
            }
            if (dataSize <= 0) return null
            val pcm = ByteArray(dataSize)
            val read = ins.read(pcm)
            if (read < dataSize) return null
            WavAudio(sampleRate, pcm)
        }
    } catch (_: Exception) {
        null
    }
}

/** 单实例 PCM 播放器：新播放打断旧的；全部调用线程安全。 */
class AudioPlayer {
    private var track: AudioTrack? = null

    @Synchronized
    fun play(wav: WavAudio) {
        stopInternal()
        val t = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(wav.sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(wav.pcm.size)
                .build()
        } catch (_: Exception) {
            null
        }
        if (t == null) return
        t.write(wav.pcm, 0, wav.pcm.size)
        runCatching { t.play() }
        track = t
    }

    @Synchronized
    fun stop() = stopInternal()

    private fun stopInternal() {
        track?.let { t ->
            runCatching { t.stop() }
            runCatching { t.release() }
        }
        track = null
    }
}

package com.readlet.app.tts

import java.io.File
import java.security.MessageDigest

/**
 * 本地合成音频的落盘缓存：filesDir/tts/audio，LRU 上限 [SpeechCache.MAX_BYTES]。
 * 键 = 音色 id + 文本；命中即刷新 lastModified（保持热数据）。写满时按最旧淘汰。
 */
class SpeechCache(private val dir: File) {

    init {
        dir.mkdirs()
    }

    fun get(voiceId: String, text: String): File? {
        val f = fileFor(voiceId, text)
        if (!f.isFile) return null
        // 触达刷新：置为当前时间，供 LRU 淘汰排序。
        runCatching { f.setLastModified(System.currentTimeMillis()) }
        return f
    }

    /** 写入缓存；返回文件。写入失败（空间等）不抛错，静默跳过缓存。 */
    fun put(voiceId: String, text: String, wav: WavAudio): File? {
        val f = fileFor(voiceId, text)
        return try {
            val tmp = File(dir, "${f.name}.tmp")
            tmp.outputStream().use { out ->
                writeWav(out, wav)
            }
            if (!tmp.renameTo(f)) {
                tmp.delete()
                return null
            }
            evictIfNeeded()
            f
        } catch (_: Exception) {
            null
        }
    }

    /** 删除某音色的全部缓存（切音色/删语音包时避免脏命中）。 */
    fun clearForVoice(voiceId: String) {
        dir.listFiles { f -> f.isFile && f.name.startsWith("$voiceId-") }
            ?.forEach { runCatching { it.delete() } }
    }

    /** 清空全部缓存（删除语音包时调用）。 */
    fun clearAll() {
        dir.listFiles { f -> f.isFile && f.extension == "wav" }
            ?.forEach { runCatching { it.delete() } }
    }

    private fun fileFor(voiceId: String, text: String): File {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest("$voiceId\u0000$text".toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }.take(16)
        return File(dir, "$voiceId-$hex.wav")
    }

    private fun evictIfNeeded() {
        val files = dir.listFiles { f -> f.isFile && f.extension == "wav" } ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_BYTES) return
        files.sortedBy { it.lastModified() }.forEach { f ->
            if (total <= MAX_BYTES) return
            val len = f.length()
            if (f.delete()) total -= len
        }
    }

    companion object {
        const val MAX_BYTES = 20L * 1024 * 1024

        /** 写标准 44 字节 RIFF/WAVE 头 + 单声道 16-bit PCM 数据。 */
        private fun writeWav(out: java.io.OutputStream, wav: WavAudio) {
            val dataSize = wav.pcm.size
            val byteRate = wav.sampleRate * 2
            fun le32(v: Int) {
                out.write(v and 0xff); out.write((v shr 8) and 0xff)
                out.write((v shr 16) and 0xff); out.write((v shr 24) and 0xff)
            }
            fun le16(v: Int) {
                out.write(v and 0xff); out.write((v shr 8) and 0xff)
            }
            out.write("RIFF".toByteArray(Charsets.US_ASCII))
            le32(36 + dataSize)
            out.write("WAVE".toByteArray(Charsets.US_ASCII))
            out.write("fmt ".toByteArray(Charsets.US_ASCII))
            le32(16); le16(1); le16(1)
            le32(wav.sampleRate); le32(byteRate); le16(2); le16(16)
            out.write("data".toByteArray(Charsets.US_ASCII))
            le32(dataSize)
            out.write(wav.pcm)
        }
    }
}

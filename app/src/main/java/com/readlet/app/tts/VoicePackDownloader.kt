package com.readlet.app.tts

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 语音包文件下载器：断点续传（Range）、多源重试（官方源失败自动切镜像）、
 * sha256 校验（[expectedSha256] 非空时）。部分文件保留 .part，取消后可续传。
 */
class VoicePackDownloader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * 下载 [urls] 中的第一个可用源到 [target]。
     * @return true=完成且（若给定）校验通过；false=所有源失败/校验失败。
     */
    suspend fun download(
        urls: List<String>,
        target: File,
        expectedSha256: String?,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): Boolean {
        for ((index, url) in urls.withIndex()) {
            val ok = try {
                downloadFrom(url, target, expectedSha256, onProgress)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            if (ok) return true
            // 本源的失败可能是文件头损坏：换源前清掉不完整主体（.part 留在原地，
            // 换源后从头重下，避免用错源拼接出坏文件）。
            if (index < urls.lastIndex) runCatching { target.partFile().delete() }
        }
        return false
    }

    private suspend fun downloadFrom(
        url: String,
        target: File,
        expectedSha256: String?,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        val part = target.partFile()
        part.parentFile?.mkdirs()
        var resumeFrom = if (part.isFile) part.length() else 0L

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Readlet/0.6 (pronunciation voice pack)")
            .apply {
                if (resumeFrom > 0) header("Range", "bytes=$resumeFrom-")
            }
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 206) return@withContext false
            if (resp.code == 416) {
                // 请求范围超出文件：服务端已无此部分 → 清掉从头下。
                part.delete()
                resumeFrom = 0
                val retry = Request.Builder().url(url).header("User-Agent", "Readlet/0.6").build()
                client.newCall(retry).execute().use { r2 ->
                    if (!r2.isSuccessful) return@withContext false
                    return@withContext streamToPart(r2, part, 0L, expectedSha256, onProgress, target)
                }
            }
            // 200 = 服务端忽略 Range，从头写。
            val start = if (resp.code == 206) resumeFrom else {
                part.delete()
                0L
            }
            return@withContext streamToPart(resp, part, start, expectedSha256, onProgress, target)
        }
    }

    private suspend fun streamToPart(
        resp: okhttp3.Response,
        part: File,
        start: Long,
        expectedSha256: String?,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
        target: File,
    ): Boolean {
        val body = resp.body ?: return false
        val digest = if (expectedSha256 != null) MessageDigest.getInstance("SHA-256") else null
        var written = start
        val total = start + (body.contentLength().coerceAtLeast(0))
        if (start > 0 && digest != null) {
            // 续传：已落盘部分计入校验上下文（流式喂入，不整读入内存）。
            val seed = ByteArray(64 * 1024)
            part.inputStream().use { ins ->
                while (true) {
                    val n = ins.read(seed)
                    if (n < 0) break
                    digest.update(seed, 0, n)
                }
            }
        }
        FileOutputStream(part, start > 0).use { out ->
            val buf = ByteArray(64 * 1024)
            body.byteStream().use { ins ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = ins.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    digest?.update(buf, 0, n)
                    written += n
                    if (total > 0) onProgress(written, total)
                }
            }
        }
        // 校验通过才落位；失败清空等待重下。
        if (expectedSha256 != null) {
            val actual = digest!!.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(expectedSha256, ignoreCase = true)) {
                part.delete()
                return false
            }
        }
        val renamed = part.renameTo(target)
        if (!renamed) {
            target.delete()
            part.renameTo(target)
        }
        onProgress(written, written)
        return true
    }

    private fun File.partFile(): File = File(parentFile, "$name.part")
}

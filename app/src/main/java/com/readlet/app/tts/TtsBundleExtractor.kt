package com.readlet.app.tts

import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

/**
 * 语音包 tar.bz2 解压到 [destDir]（去掉包内单一顶层目录）。
 * 流式逐条写入，可取消（协程取消时中止并保留已写文件，上层负责整体清理重来）。
 */
suspend fun extractTarBz2(bundle: File, destDir: File): Boolean = withContext(Dispatchers.Default) {
    destDir.mkdirs()
    try {
        BZip2CompressorInputStream(BufferedInputStream(bundle.inputStream(), 1 shl 16)).use { bz ->
            TarArchiveInputStream(bz).use { tar ->
                var entry = tar.nextTarEntry
                while (entry != null) {
                    currentCoroutineContext().ensureActive()
                    if (!entry.isDirectory) {
                        val rel = entry.name.substringAfter('/').trimStart('/')
                        if (rel.isNotBlank() && !rel.startsWith("..") && !rel.contains("/../")) {
                            val out = File(destDir, rel)
                            out.parentFile?.mkdirs()
                            FileOutputStream(out).use { fos ->
                                val buf = ByteArray(64 * 1024)
                                while (true) {
                                    val n = tar.read(buf)
                                    if (n < 0) break
                                    fos.write(buf, 0, n)
                                }
                            }
                        }
                    }
                    entry = tar.nextTarEntry
                }
            }
        }
        true
    } catch (_: Exception) {
        false
    }
}

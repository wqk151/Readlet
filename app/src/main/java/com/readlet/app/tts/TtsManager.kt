package com.readlet.app.tts

import android.content.Context
import java.io.File
import org.json.JSONArray
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 一次发音的结果类别（UI 依据它决定提示文案）。 */
enum class SpeakKind {
    /** 本地引擎合成播放。 */
    LOCAL,
    /** 系统语音播放（用户选的引擎即系统语音）。 */
    SYSTEM,
    /** 选了本地引擎但未就绪/失败，回退系统语音播放。 */
    FALLBACK_SYSTEM,
    /** 全部路径都失败（引擎不可用等），无声音。 */
    FAILED,
}

/** 发音子系统对外状态（设置页与详情页消费）。 */
data class TtsState(
    val engineId: String = TtsIds.ENGINE_SYSTEM,
    val voiceId: String = TtsIds.DEFAULT_LOCAL_VOICE,
    val packInstalled: Boolean = false,
    val downloading: Boolean = false,
    val downloadProgress: Float = 0f,
    /** 整包下载完成、正在解压。 */
    val extracting: Boolean = false,
    val downloadError: String? = null,
    /** 本地引擎首次加载中（同步读模型，约 1s 级）。 */
    val localLoading: Boolean = false,
)

/**
 * 发音管理器：进程级单例（ReadletApp 持有），所有播放/下载串行。
 * - 引擎：系统语音（默认）与本地 sherpa-onnx/kokoro v1.1 int8（懒加载常驻）；
 * - 语音包：整包单文件下载（官方 GitHub release，sha256 校验，断点续传）→ 解压到 filesDir 私有目录；
 * - 缓存：合成音频按（音色, 文本）落盘 LRU；
 * - 回退：本地未就绪时播放自动回退系统语音（调用方据 SpeakKind 提示一次）。
 */
class TtsManager(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val bundleDir = File(appContext.filesDir, "tts/bundles")
    private val packDir = File(appContext.filesDir, "tts/packs/${VoiceCatalog.PACK_ID}")
    private val cache = SpeechCache(File(appContext.filesDir, "tts/audio"))
    private val player = AudioPlayer()
    private val systemEngine = SystemVoiceEngine(appContext)
    private val downloader = VoicePackDownloader()
    private val speakMutex = Mutex()

    private val _state = MutableStateFlow(TtsState())
    val state: StateFlow<TtsState> = _state.asStateFlow()

    @Volatile
    private var localEngine: SherpaKokoroEngine? = null
    private var downloadJob: Job? = null

    init {
        bundleDir.mkdirs()
        packDir.mkdirs()
    }

    /** 启动/设置变更后同步：引擎选择 + 语音包就绪态。切回系统语音时释放本地引擎内存。 */
    fun refreshSettings(engineId: String, voiceId: String) {
        _state.update {
            it.copy(engineId = engineId, voiceId = voiceId, packInstalled = packInstalled())
        }
        if (engineId != TtsIds.ENGINE_LOCAL) releaseLocal()
    }

    /** 播放一段文本（打断式：先停当前播放）。 */
    suspend fun speak(text: String): SpeakKind = speakMutex.withLock {
        if (text.isBlank()) return SpeakKind.FAILED
        stopPlayback()
        val s = _state.value
        when {
            s.engineId == TtsIds.ENGINE_LOCAL && s.packInstalled -> speakLocal(s, text)
            s.engineId == TtsIds.ENGINE_LOCAL -> speakSystem(text, fallback = true)
            else -> speakSystem(text, fallback = false)
        }
    }

    /** 停止当前播放（离开页面/翻卡时调用）。 */
    fun stopPlayback() {
        player.stop()
        systemEngine.stop()
    }

    /** 下载并安装语音包（幂等：下载中/已装时忽略）。 */
    fun startDownload() {
        val s = _state.value
        if (s.downloading || s.packInstalled) return
        downloadJob?.cancel()
        _state.update {
            it.copy(downloading = true, extracting = false, downloadError = null, downloadProgress = 0f)
        }
        downloadJob = scope.launch {
            try {
                val bundleFile = File(bundleDir, VoiceCatalog.BUNDLE_REL)
                val ok = downloader.download(
                    urls = VoiceCatalog.bundleUrls(),
                    target = bundleFile,
                    expectedSha256 = VoiceCatalog.BUNDLE_SHA256,
                    onProgress = { downloaded, _ ->
                        val p = downloaded.toFloat() / VoiceCatalog.BUNDLE_SIZE
                        _state.update { it.copy(downloading = true, downloadProgress = p.coerceIn(0f, 1f)) }
                    },
                )
                if (!ok) {
                    // GitHub 直连失败（国内网络常见）→ 自动切换 hf-mirror 散文件集安装。
                    if (installFromMirror()) return@launch
                    failDownload("下载失败：网络异常或文件校验未通过，请重试")
                    return@launch
                }
                _state.update { it.copy(extracting = true) }
                // 失败残留（上次取消的解压产物）整体清掉重来。
                packDir.deleteRecursively()
                packDir.mkdirs()
                val extracted = extractTarBz2(bundleFile, packDir)
                if (!extracted) {
                    packDir.deleteRecursively()
                    failDownload("解压失败：文件不完整，请重新下载")
                    return@launch
                }
                runCatching { bundleFile.delete() }
                File(packDir, INSTALLED_FILE).writeText(VoiceCatalog.BUNDLE_SHA256)
                _state.update {
                    it.copy(
                        downloading = false,
                        extracting = false,
                        downloadProgress = 1f,
                        downloadError = null,
                        packInstalled = true,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failDownload("下载失败：${e.message ?: "未知错误"}")
            }
        }
    }

    /** 取消下载/解压；下载的 .part 保留，下次续传；解压残留下次整体清理。 */
    fun cancelDownload() {
        downloadJob?.cancel()
        _state.update { it.copy(downloading = false, extracting = false) }
    }

    /** 删除语音包并清理缓存（回到未安装态，播放自动回退系统语音）。 */
    fun deletePack() {
        downloadJob?.cancel()
        releaseLocal()
        cache.clearAll()
        packDir.deleteRecursively()
        File(bundleDir, VoiceCatalog.BUNDLE_REL).delete()
        _state.update {
            it.copy(
                packInstalled = false,
                downloading = false,
                extracting = false,
                downloadProgress = 0f,
                downloadError = null,
            )
        }
    }

    private suspend fun speakLocal(s: TtsState, text: String): SpeakKind =
        withContext(Dispatchers.Default) {
            val engine = ensureLocalEngine() ?: return@withContext speakSystem(text, fallback = true)
            val voice = VoiceCatalog.voice(s.voiceId) ?: return@withContext speakSystem(text, fallback = true)
            val wav = cache.get(s.voiceId, text)?.let { readWav(it) }
                ?: run {
                    val audio = engine.synthesize(text, voice.sid)
                    if (audio != null) cache.put(s.voiceId, text, audio)
                    audio
                }
            if (wav != null) {
                player.play(wav)
                return@withContext SpeakKind.LOCAL
            }
            speakSystem(text, fallback = true)
        }

    private suspend fun speakSystem(text: String, fallback: Boolean): SpeakKind {
        val ok = systemEngine.speak(text)
        return when {
            ok && fallback -> SpeakKind.FALLBACK_SYSTEM
            ok -> SpeakKind.SYSTEM
            else -> SpeakKind.FAILED
        }
    }

    /** 懒加载本地引擎（模型 ~110MB，加载约 1s 级；成功后常驻）。仅在后台线程调用。 */
    private fun ensureLocalEngine(): SherpaKokoroEngine? {
        localEngine?.let { return it }
        _state.update { it.copy(localLoading = true) }
        try {
            val engine = SherpaKokoroEngine.load(packDir)
            if (engine != null) localEngine = engine
            return engine
        } finally {
            _state.update { it.copy(localLoading = false) }
        }
    }

    private fun releaseLocal() {
        localEngine?.release()
        localEngine = null
    }

    private fun packInstalled(): Boolean = File(packDir, INSTALLED_FILE).isFile &&
        File(packDir, VoiceCatalog.MODEL_REL).isFile &&
        File(packDir, VoiceCatalog.VOICES_REL).isFile &&
        File(packDir, VoiceCatalog.ESPEAK_DATA_REL).isDirectory()

    /**
     * 镜像安装：整包不可达时，按 assets 内 manifest 逐个下载（hf-mirror.com，内容与官方整包同源）。
     * 已存在且大小/校验和的跳过（断点续传语义）；espeak-ng-data 等无 sha 文件按大小校验。
     * @return true=全部就位并写完成标记。
     */
    private suspend fun installFromMirror(): Boolean = withContext(Dispatchers.Default) {
        try {
            val text = appContext.assets.open(VoiceCatalog.MIRROR_MANIFEST_ASSET)
                .bufferedReader()
                .use { it.readText() }
            val arr = JSONArray(text)
            var total = 0L
            val entries = buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val path = o.getString("path")
                    if (path.isBlank() || path.startsWith("..") || path.contains("/../")) continue
                    val size = o.getLong("size")
                    val sha = o.optString("sha").takeIf { it.isNotBlank() }
                    total += size
                    add(Triple(path, size, sha))
                }
            }
            if (entries.isEmpty()) return@withContext false
            _state.update {
                it.copy(downloading = true, extracting = false, downloadError = null, downloadProgress = 0f)
            }
            var done = 0L
            for ((path, size, sha) in entries) {
                currentCoroutineContext().ensureActive()
                val target = File(packDir, path)
                val exists = target.isFile && target.length() == size && (sha == null || fileSha256(target) == sha)
                if (exists) {
                    done += size
                    continue
                }
                val ok = downloader.download(
                    urls = listOf(VoiceCatalog.MIRROR_BASE + path),
                    target = target,
                    expectedSha256 = sha,
                    onProgress = { downloaded, _ ->
                        val inFile = downloaded.coerceAtMost(size)
                        _state.update {
                            it.copy(downloading = true, downloadProgress = ((done + inFile).toFloat() / total).coerceIn(0f, 1f))
                        }
                    },
                )
                if (!ok || target.length() != size) return@withContext false
                done += size
                _state.update { it.copy(downloading = true, downloadProgress = done.toFloat() / total) }
            }
            File(packDir, INSTALLED_FILE).writeText(VoiceCatalog.BUNDLE_SHA256)
            _state.update {
                it.copy(
                    downloading = false,
                    extracting = false,
                    downloadProgress = 1f,
                    downloadError = null,
                    packInstalled = true,
                )
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    private fun fileSha256(file: File): String? = try {
        val digest = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(256 * 1024)
        file.inputStream().use { ins ->
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
        null
    }

    private fun failDownload(msg: String) {
        _state.update { it.copy(downloading = false, extracting = false, downloadError = msg) }
    }

    private companion object {
        const val INSTALLED_FILE = ".installed"
    }
}

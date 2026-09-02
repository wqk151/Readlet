package com.readlet.app.tts

/**
 * kokoro 美音语音包（v1.1 int8，sherpa-onnx 官方打包）。
 * 整包单文件下载：sherpa 官方 tts-models release 资产（内含 model.int8.onnx + voices.bin +
 * tokens.txt + lexicon + espeak-ng-data/），下载后解压到应用私有目录。
 * 校验和为 GitHub release asset digest（2026-09 实测）。v1.1 的英文声线仅 2 个美音女声
 * （af_maple / af_sol），另有英音女声 bf_vale 不在 v1 美音列表内。
 */
data class VoiceDef(
    /** 声线 id（= kokoro 声线名），持久化于 Settings.voiceId。 */
    val id: String,
    /** 设置页显示名。 */
    val display: String,
    /** 性别提示。 */
    val gender: String,
    /** voices.bin 内的 speaker id。 */
    val sid: Int,
)

object VoiceCatalog {

    const val PACK_ID = "kokoro-en-us"
    const val PACK_DISPLAY = "kokoro 美音语音包"

    const val BUNDLE_REL = "kokoro-int8-multi-lang-v1_1.tar.bz2"
    const val BUNDLE_SIZE = 147_031_220L
    const val BUNDLE_SHA256 = "a1e94694776049035c4f2c6529f003aaece993c76aae9a78995831c3c4dcafc6"
    const val PACK_SIZE_LABEL = "147 MB"

    /** 引擎加载所需的解压后文件（相对 packDir）。 */
    const val MODEL_REL = "model.int8.onnx"
    const val VOICES_REL = "voices.bin"
    const val TOKENS_REL = "tokens.txt"
    const val LEXICON_REL = "lexicon-us-en.txt"
    const val ESPEAK_DATA_REL = "espeak-ng-data"

    /** 美音声线（v1.1 仅此两个，均为女声）。 */
    val US_VOICES: List<VoiceDef> = listOf(
        VoiceDef("af_maple", "Maple", "女", sid = 0),
        VoiceDef("af_sol", "Sol", "女", sid = 1),
    )

    fun voice(id: String): VoiceDef? = US_VOICES.firstOrNull { it.id == id }

    /** 设置页声线标签。 */
    fun voiceLabel(v: VoiceDef): String = "${v.display} · ${v.gender}"

    /** 由持久化 id 得显示标签；未知 id 原样返回（兼容旧数据）。 */
    fun labelOf(voiceId: String): String =
        voice(voiceId)?.let { voiceLabel(it) } ?: voiceId

    /** 整包下载源（官方 GitHub release；网络受限时自动切换镜像单文件集）。 */
    fun bundleUrls(): List<String> = listOf(
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$BUNDLE_REL",
    )

    /** 镜像源：hf-mirror.com 上的同模型散文件（与官方整包同一内容，manifest 见 assets）。 */
    const val MIRROR_BASE =
        "https://hf-mirror.com/csukuangfj/kokoro-int8-multi-lang-v1_1/resolve/main/"
    const val MIRROR_MANIFEST_ASSET = "tts_mirror_manifest.json"
}

package com.readlet.app.tts

/** 语音引擎/音色的持久化标识（Settings 存字符串）。 */
object TtsIds {
    /** 系统语音：Android 系统 TTS，默认引擎，无需下载。 */
    const val ENGINE_SYSTEM = "system"

    /** 本地语音引擎：sherpa-onnx 离线神经语音（kokoro），需先安装语音包。 */
    const val ENGINE_LOCAL = "local"

    /** 默认本地音色：kokoro v1.1 美音 Maple（女，sid 0）。 */
    const val DEFAULT_LOCAL_VOICE = "af_maple"
}

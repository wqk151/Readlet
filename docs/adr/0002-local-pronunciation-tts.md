# 发音:本地引擎用 sherpa-onnx 承载 kokoro,声库按需下载

重点词/搭配短语行尾新增喇叭按钮,播放美式发音。系统 TTS 音质与美音可用性随 ROM 漂移(国产 ROM 常为中文引擎代读),本地神经引擎作为可选项提供稳定美音;默认引擎仍是系统语音,零下载开箱可用。

## Status

accepted

## Context

- 需求范围:发音条目 = 重点词(含词组形态,即 `CardWord.word`)与搭配短语;不含整句与例句。
- 代码现状:全应用零音频代码、零 native 代码;minSdk 26 / target 35;INTERNET 权限已有,但无任何 HTTP GET/下载管道。
- piper(rhasspy)本体无官方 Android 支持;sherpa-onnx(k2-fsa, Apache-2.0)官方同时支持 Kokoro-82M 与 Piper 引擎(README),带 Android 构建与示例。
- 声库体积(2026-09 实测 sherpa 官方 tts-models 发布物):kokoro 多语 v1.0 fp32 整包 349 MB(20 个美音声线);kokoro v1.1 int8 整包 147 MB(英文仅 2 美音女声 maple/sol + 1 英音女声);piper en_US 声线 ~67 MB/个(自带 espeak-ng-data)。
- kokoro 在 sherpa-onnx 中运行时依赖 espeak-ng-data 与 lexicon(词级 g2p,生词回退 espeak-ng);sherpa 只认官方整包布局(单 voices.bin + 内嵌声线表 + sid 选声),HF 上 0.5 MB/声线的散文件布局不可用。

## Decision

1. **本地引擎统一走 sherpa-onnx 一个 native 库**,同一 AAR 内可加载 kokoro 与 piper 声库,避免多引擎多 ABI 堆叠。引擎抽象(TtsEngine)保持双家族可扩展。
2. **v1 交付 kokoro v1.1 int8 整包**(147 MB 单文件下载 + 解压):美音声线仅 2 个女声(af_maple sid0 默认 / af_sol sid1),无男声;因 v1.1 是唯一有 int8 官方量化的版本,而 v1.0 完整美音声线集只有 fp32 349 MB(下载与常驻内存成本高),先保轻量与最新音质。piper(每声线 ~67 MB、含男女声)接口预留,v1.1 视男声需求再开。
3. **声库不内置 APK,按需下载**:用户选本地引擎时整包下载到 filesDir/tts(免存储权限),解压后删除压缩包;固定 URL + sha256 校验钉死在代码(校验和为 GitHub release asset digest,镜像源为单文件直链,网络波动靠断点续传兜底);支持取消、删除。
4. **默认引擎 = 系统语音**;本地引擎未就绪(未下载/加载失败)时播放自动回退系统语音,Toast 提示一次。
5. **ABI 仅 arm64-v8a**:工程首次引入 .so;2020 年后实机全覆盖,x86_64 模拟器不支持属已知限制。
6. **播放**:即时合成 + 落盘缓存(键 = 音色 + 文本,LRU 20 MB);全局单实例播放,新点击打断旧的,离开页面停止。
7. **引擎生命周期**:进程内 lazy 单例,首次本地播放时异步加载后常驻;int8 模型 ~110 MB,常驻内存估算 200-300 MB[估计],仅当用户主动启用本地引擎后产生。

## Considered Options

- **独立集成 piper 本体**:无官方 Android 产物,需自维护 native 引擎编译与 JNI,排除。
- **只用系统 TTS**:零工程,但美音质量与可用性不可控,保留为默认与回退。
- **v1 双家族全上(kokoro + piper)**:下载与运行路径翻倍(espeak-ng-data + 每声线 63 MB),收益低于成本,推迟。
- **声库内置 APK**:+86 MB 起,多数用户可能不用本地引擎,且声线升级需发版,排除。
- **多 ABI 打包**:体积与兼容矩阵成本,无模拟器需求,排除。
- **每次播放临时加载/释放引擎**:省内存但首字延迟 ~1 s+ 且抖动,排除。

## Consequences

- APK 增重 = sherpa-onnx arm64 AAR native 体积(实现期实测记录,预估 10-20 MB 级)。
- 中国网络环境依赖镜像容错;完全离线的用户停留在系统语音路径,功能不残缺。
- 无 Room 表结构变更,不 bump 数据库版本。
- 设置交互:主设置对话框新增「发音设置」入口,子对话框内引擎/音色**即时生效**(有意偏离 LLM 设置的保存制:TTS 无成本与副作用风险,避免"保存时声库未就绪"状态错乱),下载管理(进度/取消/重试/删除)在其中完成。
- 缓存清理策略:切音色与删除语音包时按前缀清理对应音频缓存。
- 发音词汇表已入 CONTEXT.md(发音/发音条目/语音引擎/系统语音/本地语音引擎/语音包/音色)。

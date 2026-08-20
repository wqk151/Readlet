
## 拾句 Readlet — Android 工程

- 产品/架构设计见 `DESIGN.md`，工程/构建说明见 `DEVELOPMENT.md`
- 构建（本机 JDK 20 + SDK `~/data/android-sdk`）：
  - `./gradlew assembleDebug` — 编译并打包 APK
  - `./gradlew testDebugUnitTest` — 跑单元测试（SM-2 / LLM 解析）
- 单元测试不用真机；修改 SM-2、AnalysisParser 后必须跑 `testDebugUnitTest`
- 新改动涉及 Room 表结构时必须同步 bump `AppDatabase.version` 并更新 `DESIGN.md` 数据模型
- **版本规范**：任何新增需求、优化、bug 修复完成且验证通过后，必须升级版本并打 release 包：
  - `app/build.gradle.kts`：`appVersionName` 语义化升级（功能 → minor，修复/优化 → patch），`versionCode` 同步 +1
  - 构建安装：`./gradlew assembleRelease` → `adb install -r app/build/outputs/apk/release/Readlet-v<versionName>-release.apk`
  - 设备上已是 release 签名（readlet-release.keystore），同签名覆盖安装数据不丢；禁止用 debug 包覆盖线上

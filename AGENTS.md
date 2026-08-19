
## 拾句 Readlet — Android 工程

- 产品/架构设计见 `DESIGN.md`，工程/构建说明见 `DEVELOPMENT.md`
- 构建（本机 JDK 20 + SDK `~/data/android-sdk`）：
  - `./gradlew assembleDebug` — 编译并打包 APK
  - `./gradlew testDebugUnitTest` — 跑单元测试（SM-2 / LLM 解析）
- 单元测试不用真机；修改 SM-2、AnalysisParser 后必须跑 `testDebugUnitTest`
- 新改动涉及 Room 表结构时必须同步 bump `AppDatabase.version` 并更新 `DESIGN.md` 数据模型


## 拾句 Readlet — Android 工程

- 产品/架构设计见 `DESIGN.md`，工程/构建说明见 `DEVELOPMENT.md`
- 构建（本机 JDK 20 + SDK `~/data/android-sdk`）：
  - `./gradlew assembleDebug` — 编译并打包 APK
  - `./gradlew testDebugUnitTest` — 跑单元测试（SM-2 / LLM 解析）
- 单元测试不用真机；修改 Sm2 / AnalysisParser / SearchMatcher / Keywords / WordLevels 后必须跑 `testDebugUnitTest`
- **编辑**：edit 操作一律用标准标记（`⟪旧│新⟫` / MATCH + `»` + REWRITE / `＋` 加行），锚点逐字节复制自文件（含缩进与 bullet 前缀）；不用 diff 风格 `-`/`+` 行
- **编辑后验证**：每次编辑 Kotlin 文件后，先跑 `lsp diagnostics`（kotlin-lsp 已配置，按需启动）确认无语法/类型错误，再构建或测试
- **UI 改动真机验证**：UI/交互改动必须按 `DEVELOPMENT.md` 2.1 SOP 真机验证（release 覆盖安装 → adb 驱动 → 截图取证），编译/单测通过不构成完成
- 新改动涉及 Room 表结构时必须同步 bump `AppDatabase.version` 并更新 `DESIGN.md` 数据模型
- **卡片写操作刷新内存态**：重新分析/掌握/取消掌握/删除等改变卡片后，同步刷新复习队列、加练队列等持有旧快照的内存态（如 `reanalyze`/`masterCard` 的 `refreshDue` 模式）
- **Compose 状态生命周期**：以卡片/词为 key 的 `remember`/`LaunchedEffect` 必须绑定影响内容的全部字段（如 `cardId` 而非仅 `status`），防止切卡残留旧数据；全屏覆盖页（详情/词频）的 `BackHandler` 必须优先于底层页面
- **版本规范**：任何新增需求、优化、bug 修复完成且验证通过后，必须升级版本并打 release 包：
  - `app/build.gradle.kts`：`appVersionName` 语义化升级（功能 → minor，修复/优化 → patch），`versionCode` 同步 +1
  - 构建安装：`./gradlew assembleRelease` → `adb install -r app/build/outputs/apk/release/Readlet-v<versionName>-release.apk`
  - 设备上已是 release 签名（readlet-release.keystore），同签名覆盖安装数据不丢；禁止用 debug 包覆盖线上

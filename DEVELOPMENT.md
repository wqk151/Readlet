# 拾句 Readlet · 开发文档

## 1. 技术栈

| 层 | 选型 | 版本 |
|---|---|---|
| 语言 | Kotlin | 2.0.21 |
| UI | Jetpack Compose + Material 3 | BOM 2024.12.01 |
| 数据库 | Room（KSP） | 2.6.1 |
| 网络 | Retrofit + OkHttp（org.json 手拼载荷，零序列化插件） | 2.11.0 / 4.12.0 |
| 异步 | Kotlin 协程 + Flow | 1.9.0 |
| 构建 | Gradle / AGP | 8.9 / 8.7.3 |
| 导航 | 状态式（sealed class + BackHandler），不引 Navigation 库 | — |
| 设置 | SharedPreferences | — |
| 测试 | JUnit 4（纯 Kotlin 单元测试） | 4.13.2 |

**选型理由**：单 Activity + 状态式导航匹配原型交互（3 Tab + 2 覆盖页），少两个依赖少两类构建风险；LLM 载荷固定两处 JSON，`org.json` 内建零成本。

## 2. 环境

```
JDK 17+（本机 Java 20 OK）
Android SDK: ~/data/android-sdk  （platforms;android-35, build-tools;35.0.0 已装，licenses 已接受）
Gradle: 使用 wrapper（8.9）
```

- `local.properties`（不入库）：`sdk.dir=/home/wqk/data/android-sdk`
- 构建命令：`./gradlew assembleDebug`（产物 `app/build/outputs/apk/debug/Readlet-v<versionName>-debug.apk`）
- APK 命名规范：`Readlet-v<versionName>-<variant>.apk`，版本号单一来源 `app/build.gradle.kts` 的 `appVersionName`（发版时同时 bump `versionCode`）
- 安装：`./gradlew installDebug`（需 adb 设备）
- 调试：真机 adb（vivo V2217A）；数据库拉取 `adb shell run-as com.readlet.app cat databases/readlet.db`

## 3. 项目结构

```
app/src/main/
├── assets/
│   ├── analyze_prompt.txt          # LLM 分析 prompt（用户底稿 + JSON 输出约束）
│   └── word_levels.tsv             # 重点词级别表：word<TAB>级别<TAB>音标<TAB>释义
│                                    # 级别: 六级/考研/雅思/专四/专八（缺省列留空）
├── java/com/readlet/app/
│   ├── ReadletApp.kt               # Application：DB/仓库/LLM/设置/词级表 单例装配
│   ├── MainActivity.kt             # 分享接收(onNewIntent) + 导航状态 + 主题
│   ├── data/
│   │   ├── db/Entities.kt          # Card / CardWord / ReviewLog + 状态枚举
│   │   ├── db/Daos.kt              # 三 DAO（Flow 查询/聚合）
│   │   ├── db/AppDatabase.kt       # version=4（v4: card_words.level）
│   │   ├── srs/Sm2.kt              # 纯 Kotlin SM-2（无 Android 依赖，可单测）
│   │   └── repo/CardRepository.kt  # 采集/分析编排/评级/加练/统计聚合
│   ├── llm/
│   │   ├── AnalyzePrompt.kt        # 读取 asset + 拼接请求（含术语表注入）
│   │   ├── AnalyzeResult.kt        # 解析产物模型
│   │   ├── AnalysisParser.kt       # org.json → AnalyzeResult（lenient）
│   │   └── LlmClient.kt            # DeepSeek chat/completions 调用 + 错误分类
│   └── ui/
│       ├── theme/                  # Color/Type/Theme（设计 token 见 DESIGN.md 4.2）
│       ├── AppViewModel.kt         # 单 ViewModel：三页状态 + 动作
│       ├── SearchMatcher.kt        # 纯 Kotlin 词级搜索匹配（可单测）
│       ├── Keywords.kt             # 纯 Kotlin 重点词拆分/词级查表（可单测）
│       └── screens/
│           ├── LibraryScreen.kt  CardDetailScreen.kt  ReviewScreen.kt
│           ├── StatsScreen.kt  WordDetailScreen.kt  SettingsDialog.kt
└── res/  mipmap-anydpi-v26 自适应图标 / values 主题
```

## 4. 关键实现要点

### 4.1 分享接收
- `MainActivity`：`launchMode=singleTask`，intent-filter 含 `ACTION_SEND`(text/plain) + `ACTION_PROCESS_TEXT`
- `onNewIntent`：读 `EXTRA_TEXT` / `EXTRA_PROCESS_TEXT` → 仓库入库 → 协程异步分析
- 来源：`getCallingPackage` 的 label（失败降级"系统分享"）

### 4.2 分析编排（CardRepository.analyzeCardInner）
```
insertCard(text, source) → status=ANALYZING
  → llm.analyze(systemPrompt + 术语表, text)（超时 60s，重试 1 次）
  → AnalysisParser 校验 schema
  → 事务：更新 card 字段 + 写入 CardWord 列表：
      a. LLM keywords 按 Keywords.splitKeyword 拆分（逗号/顿号/分号/斜杠分隔的并列项）
         → 每项一条 CardWord；若命中词级表则补 level/phonetic/meaningInContext
         → LLM 缺 pos 时从释义前缀拆出（Keywords.splitPos，如 canyon → n.）
      b. 句子分词 → 词级表查缺补漏（仅限有级别词，跳过已在 a 中的词）→ 追加 CardWord，
         上限 5 个；词表释义词性前缀拆入 pos（词性只在单词后显示）
失败分类: 网络/超时 → PENDING（下次打开自动补）
         解析失败  → FAILED（详情页可手动重试）
```
- prompt 约束（assets/analyze_prompt.txt）：keywords 每条必须含 phonetic（英式 IPA）、pos（标准缩写，动词变形标「v. (过去式/现在分词/第三人称单数)」，短语标「n. phrase / phr. v.」）、meaning_in_context（纯中文，不得以词性缩写开头）——保证卡片内字段位置统一。

### 4.3 重点词级别（todo #4）
- 资产 `assets/word_levels.tsv`（80,440 词，约 4.7MB，APK 压缩后约 1.6MB）：
  - 级别词 16,241：六级 2535 + 考研 2572 + 雅思 2807 + 专四 2592 + 专八 5735
  - **无级别兜底词 64,199**（柯林斯缓存 `~/.cache/kdcache/kd_data.db` 全量单 token 词，仅提供音标/释义兜底，不参与补缺、不显示角标）
- 数据源：本地柯林斯词典缓存（`kd_data.db`：音标/释义，84k 词）+ 开源词表（考研/雅思/六级/专四/专八级别标签）。TSV 生成时用 kd_data 回填了级别词的音标缺口（9,571 缺音标中 5,312 个已回填，如 loom → [luːm]）。
- 优先级（一词多表取高）：专八 > 雅思 > 考研 > 六级 > 专四。
- 运行时：`WordLevels` 启动时加载为 `HashMap<word, Entry>`；查词走**变形还原**（原形 → ing/ed/ies/es/s 后缀剥离回退），O(1) 每词，零 LLM 成本。
- 分析时对已分析句子做补充：句中每个 token 查表，命中且未被 LLM 关键词覆盖（含词组包含）则加为 CardWord（仅限**有级别**的词，上限 5 个）；LLM 关键词本身命中级别也补角标，未命中词表则用兜底词的音标/释义。
- 音标/释义兜底一律过 blank 守卫（词表空串 → null），避免 UI 渲染空行。
- 词性位置统一：词表释义词性前缀（「n. 峡谷」）拆入 pos 字段（`Keywords.splitPos`），释义去前缀；LLM 缺 pos 时同样从释义拆出——词性只出现在单词后。
- 简单词过滤：LLM 关键词命中词表且为四级基础词（fog/racket/wake 类）→ 剔除；词表未收录的词保留。
- 展示：级别标记在音标下一行（复习答案面 / 卡片详情页一致，12sp 与音标同字重色），`Keywords.levelLabel` 映射 六级→CET6 / 专四→TEM4 / 专八→TEM8 / 雅思→IELTS / 考研→考研；无级别不显示。

### 4.4 重点词拆分（todo #3）
- LLM 偶尔把并列结构当一个关键词返回（如 `bottle fame, brew glory, stopper death`）。
- `Keywords.splitKeyword`：按 `, ， 、 ; ； /` 拆开，逐项 trim、去尾部标点、丢弃空项与单字符，卡内去重。
- 入库即拆分；**统计词频榜/去重词数/按词查句**全部改为 Kotlin 侧拆分后重聚合（个人数据量小，直接拉全表），旧数据无需迁移。

### 4.5 搜索匹配（todo #8）
- 位置：`ui/SearchMatcher.kt`（纯 Kotlin，可单测）。
- **词级匹配，不做跨词子序列**（旧 fzf 式匹配会让 `couple` 命中一整段里散布 c/o/u/p/l/e 的任意长句）。
- 单查询词：对句中每个词打分 —— 整词=100 / 词首=60 / 词内包含=30，取最高；0 分不命中。
- 词组查询（含空格）：按整短语做词序列匹配，命中=100。
- 句子与重点词都参与匹配，取最高分排序；命中段直接由匹配结果给出（整词/整短语高亮）。
- 搜索/筛选变化时 `LazyListState.scrollToItem(0)` 回顶（todo #1）。

### 4.6 卡片库 UI（todo #1/#2/#5）
- LazyColumn + `rememberLazyListState` + `VerticalScrollbar`（`rememberLazyListScrollbarAdapter`）。
- 头部显示「共 N 条」；每张卡显示**全局序号**（按 createdAt 倒序排名 #N），复习页显示同一编号（`AppViewModel.cardSeq` 派生），两处可互相对照。

### 4.7 复习页渲染（todo #6）
- 根因：切页首帧队列未加载完先显示空态（默认字体），随后才渲染衬线句子 → 字体跳变。
- `ReviewSession.ready`：队列首次加载完成后置位；未 ready 且无卡时显示「加载中…」，ready 且无卡才显示「今日复习完成」。
- 重点词由 ViewModel 预载（`reviewWords` 随 session.card 变化加载），首帧即带高亮渲染，无异步弹出。

### 4.8 术语表（todo #9）
- 设置项 `glossary`（SharedPreferences，多行文本，每行 `词 → 译法`，分隔符支持 `→`/`=`/`：`）。
- 分析请求时注入 system prompt 末尾：「术语表（以下术语必须按指定译法翻译）：term1→译1；term2→译2」。

### 4.9 SRS
- `Sm2` object：纯函数 `next(card, quality) → newSrs`，单测覆盖（间隔序列 1/6/…、ef 下限、失败重置）
- 复习队列：`dueAt≤today` 按 dueAt 升序；待学习卡（dueAt=null）不进队列
- 加练：`practiceQueue` 内存态，只写 ReviewLog(type=1)

### 4.10 学习流程（todo #7 · 方案 A）
- 数据语义：**待学习** = 已分析 && `dueAt=null` && 未掌握；分享后的卡一律先「待学习」，不进复习队列。
- 「开始学习」（详情页按钮 / `CardRepository.learnCard`）：`dueAt = 明天 epochDay`，次日进入排程。
- 每日队列只返回 `dueAt≤today` 的卡（`CardDao.dueQueue`）；原「新卡自动进队 + 每日 10 张上限」逻辑与 `newCardsPerDay` 设置已移除。
- 卡片库：角标四态（待分析/分析中/失败/已分析 + 琥珀「待学习」）；筛选「未复习」改为「待学习」。
- 旧数据迁移：DB v5 数据迁移将 `dueAt=null 且未掌握` 的卡置为次日到期（旧卡视为已学习）。

### 4.11 统计
- 热力图/曲线：`reviewlog GROUP BY reviewedDay` → Kotlin 侧算连续天数与周布局
- 词频榜：`cardword` 全量拉取 → Kotlin 拆分重聚合（见 4.4），展示"出现 N 次 · M 句"
- 图表：Compose Canvas 手绘（热力图 grid + 折线），不引图表库

## 5. 数据库版本

| 版本 | 变更 |
|---|---|
| 1 | 初始：cards / card_words / review_logs |
| 2 | cards.mastered |
| 3 | llm_usage 表 |
| 4 | card_words.level（重点词级别角标，可空） |
| 5 | 学习门槛（方案 A）：旧数据 `dueAt=null 且未掌握` → 次日到期（无表结构变化） |

迁移见 `AppDatabase.MIGRATION_*`；Room schema 变更必须同步 bump 版本并更新 DESIGN.md 数据模型。

## 6. 测试

- `Sm2Test`：间隔序列、ef 调整、失败重置、lapse
- `AnalysisParserTest`：正常 JSON、代码围栏包裹、缺字段、mode 识别
- `SearchMatcherTest`：整词/词首/词内/词组命中、大小写、跨词不命中、短语不含空格
- `KeywordsTest`：拆分规则、卡内去重、变形还原查表、级别优先级
- Room 层：instrumented 测试（`connectedDebugAndroidTest`），可推迟到有设备后

## 7. 约定

- 包名 `com.readlet.app`，minSdk 26 / target 35
- 不引入 kapt（统一 KSP）；不引入 Navigation/Hilt/DataStore（v1 控制依赖面）
- LLM 输出一律原文存 JSON 字符串，展示层再解析（保留信息、便于迁移 FSRS/导出）
- 文案中文，句子正文用 serif 字体（ui-design.html 同步为准）
- 词级资产与算法必须是纯 Kotlin（无 Android 依赖）以便单测；Android 资产读取只在加载层做

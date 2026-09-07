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
- **发布流程（规范）**：任何新增需求、优化、bug 修复完成后 → bump `appVersionName`（功能 minor / 修复 patch）+ `versionCode` +1 → `./gradlew assembleRelease`（自动走 `keystore.properties` 的 `readlet-release.keystore` 签名）→ `adb install -r app/build/outputs/apk/release/Readlet-v<versionName>-release.apk`（同签名覆盖，数据不丢；设备上已装 release 签名，勿用 debug 包覆盖）
- 调试：真机 adb（vivo V2217A）；数据库拉取 `adb shell run-as com.readlet.app cat databases/readlet.db`

### 2.1 真机验证 SOP（UI 改动必读）

- **安装**：`./gradlew assembleRelease` → `adb install -r app/build/outputs/apk/release/Readlet-v<versionName>-release.apk`（同签名覆盖，数据不丢；**禁止 debug 包覆盖线上**）；装完 `adb shell dumpsys package com.readlet.app | grep -m2 versionName` 确认版本生效
- **启动/复位**：`adb shell am force-stop com.readlet.app && adb shell am start -n com.readlet.app/.MainActivity`（force-stop 清掉内存态 UI 状态，如搜索框残留）
- **坐标定位**：先 `adb shell uiautomator dump /sdcard/ui.xml && adb pull`，解析 XML 里节点的 `text` 与 `bounds`（Compose 节点同样可见），再 `adb shell input tap X Y`——**不要目测截图估坐标**（dp 换算易点偏，且无失败反馈）
- **输入/滚动**：`adb shell input text "130"`（纯 ASCII）；`adb shell input swipe X1 Y1 X2 Y2 400`；键盘弹出会遮挡内容，截图前先 `adb shell input keyevent 4` 收键盘
- **截图（关键坑）**：
  - `adb exec-out screencap -p` 管道输出在多显示器/管道场景常损坏（`file` 检测为 `data`）→ **必须** `adb shell screencap -p /sdcard/x.png && adb pull /sdcard/x.png /tmp/x.png`（多显示器 warning 时加 `-d <display-id>`）
  - 图片分析用 **inspect_image**（`write` JSON 到 `xd://inspect_image`，字段 `path` + `question`）——`read` 工具读不了 PNG 二进制
  - 分析前 `file /tmp/x.png` 应输出 `PNG image data`，否则重截
  - 用完清理：`adb shell rm /sdcard/x.png` 与本地临时文件
- **状态验证**：数据库 `adb shell run-as com.readlet.app cat databases/readlet.db`（或 pull 后用 sqlite 查询）

## 3. 项目结构

```
app/src/main/
├── assets/
│   ├── analyze_prompt.txt          # LLM 句子/词分析 system prompt（字段约束 + 术语表注入位）
│   ├── analyze_etymology.txt       # LLM 词源分析 system prompt（词族拆解/推导义，VERSION 控缓存）
│   ├── word_levels.tsv             # 80k 词级表：word<TAB>级别<TAB>英标<TAB>美标<TAB>释义<TAB>四级标志
│   │                                # （本地可信静态事实：音标/级别/释义）
│   ├── wordroot.txt                # 词根词缀数据集（772 条 root/prefix/suffix + 例词：词源入口/词族/本地拆解）
│   └── tts_mirror_manifest.json    # 本地发音引擎语音包下载清单（URL 前缀 + 文件相对路径/大小）
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

### 3.1 assets/ 资产清单与 LLM 搭配

全部资源打包进 APK（`app/src/main/assets/`），运行时只读、可离线。五类资产分两类角色：**LLM prompt**（两个文本）与 **本地可信数据**（词表/词根数据集/语音包清单）。

| 文件 | 体量 | 内容与来源 | 加载点 | 主要消费方 | 与 LLM 的关系 |
|---|---|---|---|---|---|
| `analyze_prompt.txt` | 10 KB | 句子/词分析 system prompt（用户底稿 + JSON 输出 schema 与字段约束）；人工维护 | `AnalyzePrompt.system()` | `CardRepository.analyzeCardInner` | 句子分析的 **prompt 本体**（请求时拼接术语表），LLM 按它产出结构化 JSON |
| `analyze_etymology.txt` | 3 KB | 词源分析 system prompt（词根 → 词族每词 breakdown/推导义，JSON schema）；人工维护 | `EtymologyPrompt.system()` | `ensureEtymology`（词源库生成） | 词源分析的 **prompt 本体**；改动须 bump `EtymologyPrompt.VERSION` |
| `word_levels.tsv` | 5.4 MB（APK 内 ~2.0） | 80,440 词：`word<TAB>级别<TAB>英标<TAB>美标<TAB>释义<TAB>四级标志`（柯林斯缓存 + 考研/雅思/六级/专四/专八表） | `WordLevels.load()`（启动一次，HashMap） | 分析落地（音标/级别兜底）、`KeywordFill` 补缺、`backfillWordGaps`、词根页词族音标、UI 元信息行 | **可信兜底**：LLM 字段落地前以词表优先（`phonetic/level` 用词表值覆盖 LLM 缺失项）；**不进 prompt** |
| `wordroot.txt` | 325 KB（APK 内 ~75） | 词根词缀数据集 772 条（class=root 503/prefix 117/suffix 152），每条 meaning/class/root/origin/example[]（ECDICT MIT + 蒋争《英语词汇的奥秘》扩充，见 ADR 0003） | `Roots.load()`（启动一次，三索引） | 词源入口判定、词根页词族、本地拆解/回退、`lexicon` 词根归属 | **LLM 词源生成的闸门与输入**（见下）；**不进 prompt** |
| `tts_mirror_manifest.json` | 18 KB | 本地发音引擎语音包下载清单（`{path,size}` + hf-mirror URL 前缀） | `TtsManager`/`VoiceCatalog` | 本地 TTS（sherpa-onnx 引擎包按需下载，不预装 APK） | **无关**：纯本地发音链路，见 ADR 0002 |

**两个 prompt 资产与 LLM 的分工**

- `analyze_prompt.txt` 是句子分析唯一 prompt：system = 资产全文 + 术语表（设置 `glossary`）注入；LLM 输出由 `AnalysisParser` 校验解析为 `AnalyzeResult`。改 schema/字段约束时**必须同步** `AnalysisParser`/`AnalyzeResult` 及其单测（`AnalysisParserTest`）。
- `analyze_etymology.txt` 是词源生成唯一 prompt：给定词根 + 构词义 + 词族（来自 wordroot），LLM 逐词给 breakdown/推导义，`EtymologyParser` 解析后落地 `root_etymology`（带 `promptVersion`）。**prompt 改动必须 bump `EtymologyPrompt.VERSION`**——旧缓存（`promptVersion < VERSION`）在读取时自动判失效、重新生成。

**静态数据与 LLM 的源可信分层**（ADR 0003：本地可信数据优先，LLM 只兜底、不确定→null 禁编造）

- **词表（word_levels）**管"静态事实"：音标（英/美）、级别、原型、词典释义。分析落库时逐字段 `词表值 ?: LLM 值`（如 `phonetic = wordLevels.phoneticOf(part) ?: k.phoneticUk`）；词表查缺补漏只加**有级别**词（`KeywordFill`，上限 5）；启动 `backfillWordGaps` 幂等回填历史缺口。词表同时给词根页词族词提供音标与词典义兜底。
- **词根数据集（wordroot）**管"词根锚点 + 词族"，与 LLM 词源生成三处配合：
  1. **触发闸门**：每次分析后 `triggerEtymologyFor` 只对 `wordToRoot` 命中的词触发 LLM 词源生成（按词根去重、幂等、异步不阻塞）——词根由本地数据集认定，LLM 不参与"某个词属于哪个词根"的判定，杜绝漂移/臆造；
  2. **生成输入**：发给 LLM 的词族 = 该词根的 example[]，LLM 只在给定词族内逐词拆解，不自由枚举；
  3. **离线/失败回退**：词源库未生成或 LLM 失败时，词根页回退本地 `breakdownOf`（前缀+词根+后缀交叉推导，不虚构）+ 词族行 + 词表词典义。重点词构词字段同理：`affix = roots.breakdownOf ?: LLM affix`。
- **数据扩充自动生效**：wordroot/词表是纯数据，扩充（如 2026-09 +80 词根）只替换资产即可，新词根的词源入口/词族/拆解由索引自动覆盖，无需改代码与 Room。

**变更须知（改资产时逐条核对）**

- `analyze_prompt.txt`：schema 改动 → 同步 `AnalysisParser`/`AnalyzeResult`/`AnalysisParserTest`；句子分析 prompt 无版本字段（不落库），改完重分析即有。
- `analyze_etymology.txt`：→ 同步 `EtymologyParser` + bump `EtymologyPrompt.VERSION`（触发旧缓存重生成，见 CardRepository.loadEtymologyItems）。
- `word_levels.tsv`：重新生成链路见 4.3（kd_data.db + `tools/fill_phonetics.py`）；收词须过 8 万词表校验的纪律见 ADR 0003；词表升级后 `backfillWordGaps` 会在下次启动自动补齐旧数据。
- `wordroot.txt`：替换/扩充须保持 772 条 JSON 结构与 class 口径；并入新词根/例词时遵循 ADR 0003 质量规则（词表校验、防 word→root 碰撞、数字消歧条目不并入）。
- `tts_mirror_manifest.json`：内容须与远端镜像实际文件（`size` 逐文件一致，下载校验）同步；引擎升级时 URL 前缀与 manifest 一起换。
- **缺省行为**：prompt 资产缺失 → 代码内置一句话兜底（不应发生）；数据资产缺失 → 加载为空表、相关功能静默失效（`WordLevels`/`Roots` 均如此），**禁止删除**。

## 4. 关键实现要点

### 4.1 分享接收
- `MainActivity`：`launchMode=singleTask`，intent-filter 含 `ACTION_SEND`(text/plain) + `ACTION_PROCESS_TEXT`
- `onNewIntent`：读 `EXTRA_TEXT` / `EXTRA_PROCESS_TEXT` → 仓库入库（仅置待分析，不自动分析）
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
失败分类: 网络/超时 → PENDING（一键分析手动补）
         解析失败  → FAILED（详情页可手动重试）
```
- prompt 约束（assets/analyze_prompt.txt）：keywords 每条必须含 phonetic（英式 IPA）、pos（标准缩写，动词变形标「v. (过去式/现在分词/第三人称单数)」，短语标「n. phrase / phr. v.」）、meaning_in_context（纯中文，不得以词性缩写开头）——保证卡片内字段位置统一。

### 4.3 重点词级别（todo #4）
- 资产 `assets/word_levels.tsv`（82,064 行，原始约 5.5MB，APK 压缩后约 2.1MB）：
  - 级别词 16,241：六级 2535 + 考研 2572 + 雅思 2807 + 专四 2592 + 专八 5735
  - **无级别兜底词 65,823**（柯林斯缓存 `~/.cache/kdcache/kd_data.db` 全量单 token 词 + 词根词族生僻词补行，仅提供音标/释义兜底，不参与补缺、不显示角标）
- 数据源：本地柯林斯词典缓存（`kd_data.db`：音标/释义，84k 词）+ 开源词表（考研/雅思/六级/专四/专八级别标签）。TSV 生成时用 kd_data 回填了级别词的音标缺口（9,571 缺音标中 5,312 个已回填，如 loom → [luːm]）。
- 音标缺口维护：`tools/fill_phonetics.py`（有道词典页面抓取英/美音标，幂等、断点续跑；支持 `--list` 文件批量与表外新词插行）——kd_data 无数据的词用 `python3 tools/fill_phonetics.py` 批量补；词表与有道都缺的生僻词可由《英语词汇的奥秘(音标精排版)》PDF 兜底（`tools/extract_jz_pdf_phonetics.py`，仅词族词、仅填英标缺口，书音标为老式转写）。
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
- 头部显示「共 N 条」；每张卡显示**固定编号** `#<Card.id>`（v0.5 起：AUTOINCREMENT 永不复用、备份恢复不变），复习页/详情页显示同一编号，两处可互相对照；搜索框输入纯数字按编号定位（未命中显示专属空态）。取代 v0.4 的「按 createdAt 倒序排名」全局序号（随增删变化，不可作稳定引用；`AppViewModel.cardSeq` 已移除）。

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

### 4.12 发音知识库（统计页入口）
- 文档见 `DESIGN.md` §14。导航：`Overlay.Knowledge(KnowledgeDoc)`（Overlay 栈复用）→ `ui/screens/KnowledgeScreen.kt`。
- 内容数据在 `ui/knowledge/`：`Kb.kt`（`KbSection`/`KbBlock` 模型 + 渲染：表格=按列权重网格 + `**…**` 加粗解析；Code 块横向滚动；Tip 提醒块）与两份数据 `PronRulesContent.kt`/`IpaContent.kt`。内容更新 = 改 Kotlin 数据 + `KnowledgeScreen.kt` 里 `pages` 映射文案（meta/intro），无 Room / assets / 网络参与。

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

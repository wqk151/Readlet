# 复习正面划词 · 自绘划词菜单(方案 B,未实施)

> 状态:**已冻结的备选方案**。2026-08-18 设备验证发现 vivo OriginOS 的系统划词工具栏裁剪第三方 PROCESS_TEXT 应用,方案 A(系统工具栏)在此类 ROM 上无法显示欧路/ChatGPT。用户选择暂维持方案 A(见 `docs/adr/0001-review-front-selection.md`)。本文件是方案 B 的完整实施蓝图,届时直接照此实现即可,无需重新排查。

## 目标

复习正面长按选中后,菜单显示:**复制 / 全选 / 欧路词典 / ChatGPT / Kimi(已安装才显示)/ Readlet 查词库**(可按需求加设置页勾选,实现「只显示欧路或 ChatGPT」)。

## 现状(方案 A,已上线)

- `ReviewScreen` 正面句子 = `AndroidView` + `TextView`(`setTextIsSelectable(true)`),高亮用 `sentenceSpannable()`(Common.kt)
- 系统划词工具栏:原生 Android 显示全部 PROCESS_TEXT 应用;vivo OriginOS 只显示 复制/全选/翻译/搜索/分享

## 实施步骤

### 1. manifest 增加 `<queries>`(targetSdk 35 查询应用必需)

```xml
<queries>
    <intent>
        <action android:name="android.intent.action.PROCESS_TEXT" />
        <data android:mimeType="text/plain" />
    </intent>
</queries>
```

### 2. ReviewScreen 的 TextView 挂自定义 ActionMode

在现有 `factory` 里追加:

```kotlin
setCustomSelectionActionModeCallback(object : ActionMode.Callback {
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        // 自绘菜单:复制 / 全选 / 已装 PROCESS_TEXT 应用 / Readlet 查词库
        return true
    }
    override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false
    override fun onDestroyActionMode(mode: ActionMode) {}
    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        // 见下方各菜单项实现
    }
})
```

实现要点:

- **复制**:`ClipboardManager.setPrimaryClip(ClipData.newPlainText("", tv.text.subSequence(start, end)))`
- **全选**:`tv.setSelection(0, tv.length())`;选中文本用 `tv.text.subSequence(tv.selectionStart, tv.selectionEnd)`
- **第三方应用项**:构造 PROCESS_TEXT intent 后 `startActivity`:
  ```kotlin
  Intent(Intent.ACTION_PROCESS_TEXT)
      .setType("text/plain")
      .putExtra(Intent.EXTRA_PROCESS_TEXT, selected)
      .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
      .setPackage(pkg)   // 欧路词典等目标包名
  ```
- **查词库项**:选中文本 → `vm.openWord(text)`(复用 `MainActivity` 划词查库逻辑;不采集、不 moveTaskToBack)

### 3. 应用解析与过滤

```kotlin
packageManager.queryIntentActivities(
    Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain"), 0,
)
```

- 跳过 Readlet 自己(以专用「查词库」菜单项替代)
- 可选扩展:设置页勾选制(静读天下模式),勾选结果存 `Settings.kt`(SharedPreferences 已存在);默认只勾欧路词典 + ChatGPT

### 4. 验证清单

- [ ] vivo(OriginOS):菜单出现 欧路/ChatGPT/Kimi/Readlet 查词库
- [ ] 原生 Android:菜单内容一致,行为正常
- [ ] 复制/全选行为与系统一致
- [ ] 划词跳查:欧路词典收到选中文本
- [ ] 划词查库:返回键回复习,不采集、不打断
- [ ] 外部回归:ACTION_SEND 分享采集不受影响

## 已核实的事实(2026-08-18,无需重查)

- Compose `SelectionContainer` 工具栏只有复制/全选,无 PROCESS_TEXT(androidx 源码 + issue #139321320)
- 原生 TextView 系统工具栏在原生 Android 上包含 PROCESS_TEXT 应用;vivo OriginOS 裁剪之(真机实测:系统 EditText、Chrome、Readlet 三处一致)
- 欧路词典、Google 翻译确认 PROCESS_TEXT;ChatGPT 有声明但点选后文本处理不可靠;Kimi 无权威证据(2026-08 检索)
- 剪贴板复制保留内建 span(颜色/加粗),自定义 span 被剥掉
- 已知坑:自定义 ActionMode 下硬件键盘的 copy/paste 无法走原生拦截(stackoverflow 68956792 相关讨论);OEM ROM 行为差异属常态,验证清单覆盖

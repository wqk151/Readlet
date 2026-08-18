# 复习正面划词用原生 TextView 承接系统划词工具栏

复习正面需要支持长按选中并交给欧路词典等第三方查词应用(划词跳查)。Compose 的 `SelectionContainer` 默认工具栏只有复制/全选,不含任何 PROCESS_TEXT 应用(androidx issue #139321320,源码无 process text 逻辑),因此正面句子改用 `AndroidView` 包裹原生 `TextView`(`textIsSelectable = true`),由系统生成完整划词工具栏——它必然包含所有已装 PROCESS_TEXT 应用。Readlet 因已注册 PROCESS_TEXT 也出现在自己的工具栏里:点击时按前台来源判定,不采集新卡,改为打开词频详情页查词库(划词查库),不打断复习。

## Status

accepted

## Considered Options

- **Compose SelectionContainer 默认工具栏**:复制/全选之外没有任何第三方应用,不满足需求,排除。
- **自绘划词菜单(静读天下式,自定义 TextToolbar/ActionMode + PackageManager 解析 + 设置页勾选)**:可控制只显示欧路/ChatGPT,但需自绘弹层、自实现复制/全选,实现量大。用户明确选择系统默认工具栏,接受菜单无法过滤。
- **原生 TextView 系统工具栏(选定)**:菜单内容由系统决定,显示所有声明 PROCESS_TEXT 的应用(欧路词典、Google 翻译、可能 ChatGPT,以及 Readlet 自己),无法过滤、无法隐藏自己;换来最小的实现量与系统级可靠性。

## Consequences

- 菜单内容不可控:所有声明 PROCESS_TEXT 的应用都会出现;Kimi 无 PROCESS_TEXT 证据不会出现,ChatGPT 支持不稳定(文本可能被忽略)。
- 仅复习正面启用划词;其他页面的句子仍是 Compose `Text`,无选中能力。
- 选中复制到剪贴板保留内建 span(颜色/加粗),自定义 span 会被剥掉。
- **vivo OriginOS 机型(设备实测,2026-08-18)**:系统划词工具栏裁剪所有第三方 PROCESS_TEXT 应用,只显示 ROM 项目(复制/全选/翻译/搜索/分享)。此 ROM 上 划词跳查/划词查库/划词采集(经系统工具栏)均不可达;原生 Android 不受影响。若需在此类 ROM 上展示第三方应用,须自绘划词菜单(阅读 app 模式),蓝图见 `docs/future-custom-selection-menu.md`。

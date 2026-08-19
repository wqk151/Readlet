- [x] 需求：希望在复习界面的正面实现长按选中英文 →通过Android的ACTION_PROCESS_TEXT + Intent + Intent Filter机制在菜单里出现“ChatGPT / Kimi / 欧路词典 / 翻译”等选项 → 点击后把选中的文字直接交给对应 App。
大概流程：
```
app 复习正面的语句
   │
   │ 用户长按
   ▼
选中 "serendipity"
   │
   ▼
Android Text Selection Toolbar
   ├── 复制
   ├── 分享
   ├── 搜索
   ├── ChatGPT
   ├── Kimi
   ├── 欧路词典
   └── 其他支持 PROCESS_TEXT 的 App
```

**已实现(2026-08-18)**:复习正面句子改用原生 TextView(textIsSelectable),由系统生成划词工具栏;点 Readlet 自己 → 划词查库(词频详情,不打断复习)。详见 `docs/adr/0001-review-front-selection.md`。

**已知限制**:vivo OriginOS 系统工具栏裁剪第三方 PROCESS_TEXT 应用(欧路/ChatGPT/Readlet 均不显示),此 ROM 上划词跳查/查库/采集不可达;原生 Android 正常。如需在此类 ROM 上显示第三方应用,按 `docs/future-custom-selection-menu.md` 的自绘菜单方案 B 实施(已冻结,未动工)。

- [ ] 假如有4个待分析的句子，点击一键分析后该按钮应该变成分析中（带进度0/4），切不可点击，这四个句子都应该显示分析中，只不过后台按顺序进行分析（或按2并发？）
- [ ] 分享来源不是书名，一直是系统分享，是否可以获取真正的来源或书名?

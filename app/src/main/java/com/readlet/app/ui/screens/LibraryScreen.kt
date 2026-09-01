package com.readlet.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readlet.app.data.db.CardStatus
import com.readlet.app.ui.AppViewModel
import com.readlet.app.ui.CardSurface
import com.readlet.app.ui.EmptyHint
import com.readlet.app.ui.InboxItem
import com.readlet.app.ui.LearnChip
import com.readlet.app.ui.MetaLine
import com.readlet.app.ui.SearchMatcher
import com.readlet.app.ui.StatusChip
import com.readlet.app.ui.theme.Amber
import com.readlet.app.ui.theme.Blue
import com.readlet.app.ui.theme.Green
import com.readlet.app.ui.theme.Muted
import com.readlet.app.ui.theme.Red
import com.readlet.app.ui.theme.SentenceType

private enum class LibFilter(val label: String) {
    ALL("全部"), TO_LEARN("待学习"), MASTERED("已掌握"), PENDING("待分析")
}

/** 卡片库分页大小。 */
private const val PAGE_SIZE = 30

/**
 * 卡片库：搜索（词级匹配）+ 筛选 + 状态 + 已学次数；分页浏览。
 * 搜索/筛选变化时回顶（命中排序变化后定位不再停留在老位置）。
 */
@Composable
fun LibraryScreen(vm: AppViewModel) {
    val items by vm.inboxItems.collectAsStateWithLifecycle()
    val streak by vm.streak.collectAsStateWithLifecycle()
    val reviewCounts by vm.reviewCounts.collectAsStateWithLifecycle()
    val analyzing by vm.analyzing.collectAsStateWithLifecycle()
    val detailAnalyzing by vm.detailAnalyzing.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(LibFilter.ALL) }
    var visibleCount by remember { mutableStateOf(PAGE_SIZE) }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    val q = query.trim()
    // 编号定位：纯数字查询按固定编号（Card.id，AUTOINCREMENT 永不复用）精确匹配；
    // 否则词级搜索：句子或任一重点词命中（整词/词首/词内包含/词组），按匹配分排序。
    val idQuery = q.toLongOrNull()
    val filtered = if (q.isEmpty()) {
        items.filter { item -> matchFilter(item, filter) }
    } else if (idQuery != null) {
        items.filter { matchFilter(it, filter) && it.card.id == idQuery }
    } else {
        items.mapNotNull { item ->
            if (!matchFilter(item, filter)) return@mapNotNull null
            val sentenceScore = SearchMatcher.score(q, item.card.text)
            val wordScore = item.words.maxOfOrNull { SearchMatcher.score(q, it.word) } ?: 0
            val best = maxOf(sentenceScore, wordScore)
            if (best == 0) null else item to best
        }.sortedByDescending { it.second }.map { it.first }
    }

    // 搜索词/筛选条件变化时回到第一页（结果排序变化后定位跟随新结果）。
    LaunchedEffect(q, filter) {
        visibleCount = PAGE_SIZE
        listState.scrollToItem(0)
    }

    // 点击空白处（非搜索框）收起键盘/取消聚焦
    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }
    ) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "卡片库",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                "共 ${items.size} 条",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Muted,
            )
            Spacer(Modifier.weight(1f))
            if (streak > 0) {
                Text(
                    "🔥 连续 $streak 天",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Amber,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
            // 一键分析：手动补分析所有待分析/失败卡（收藏不再自动分析）。
            // 批量分析中 → 显示「分析中 done/total」且不可点击；结束后恢复按钮（失败卡仍计入）。
            val needAnalysis = items.count { it.card.status != CardStatus.ANALYZED }
            val batch = analyzing
            if (batch != null || needAnalysis > 0) {
                if (batch != null) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Blue.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            "分析中 ${batch.done}/${batch.total}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Blue,
                        )
                    }
                } else {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Red.copy(alpha = 0.12f))
                            .clickable { vm.analyzeAll() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            "一键分析 $needAnalysis",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Red,
                        )
                    }
                }
            }
            IconButton(onClick = { vm.settingsOpen.value = true }) {
                Icon(Icons.Filled.Settings, contentDescription = "设置",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SearchField(query, onSearch = { focusManager.clearFocus() }) { query = it }
        // 筛选计数：基于全量库（不受搜索词影响），>0 才显示在 chip 文字内。
        val learnCount = items.count { matchFilter(it, LibFilter.TO_LEARN) }
        val masteredCount = items.count { matchFilter(it, LibFilter.MASTERED) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibFilter.entries.forEach { f ->
                FilterChip(
                    f.label,
                    f == filter,
                    count = when (f) {
                        LibFilter.TO_LEARN -> learnCount
                        LibFilter.MASTERED -> masteredCount
                        else -> 0
                    },
                ) { filter = f }
            }
        }
        Spacer(Modifier.height(10.dp))
        when {
            items.isEmpty() -> EmptyHint(
                "还没有拾到句子",
                "阅读时选中喜欢的句子 → 分享 → 选择「拾句」，\n即可收藏，点「一键分析」或详情页「开始分析」生成可复习的卡片。",
            )
            filtered.isEmpty() -> if (idQuery != null) {
                EmptyHint("没有找到编号 #$idQuery 的卡片", "编号与卡片一一对应；输入卡片库/复习页显示的编号即可定位。")
            } else {
                EmptyHint("没有符合条件的卡片", "换个关键词或筛选条件试试。")
            }
            else -> Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(end = 6.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
                ) {
                    items(filtered.take(visibleCount), key = { it.card.id }) { item ->
                        LibRow(
                            item,
                            reviewCount = reviewCounts[item.card.id] ?: 0,
                            query = q,
                            batchRunning = analyzing != null,
                            onClick = {
                                focusManager.clearFocus()
                                vm.openDetail(item.card.id)
                            },
                            reanalyzing = item.card.id in detailAnalyzing,
                            onReanalyze = { vm.reanalyze(item.card.id) },
                            onDelete = { vm.deleteCard(item.card.id) },
                        )
                    }
                    if (filtered.size > visibleCount) {
                        item(key = "load_more") {
                            Box(
                                Modifier.fillMaxWidth().padding(top = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .clickable { visibleCount += PAGE_SIZE }
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "加载更多（剩余 ${filtered.size - visibleCount} 条）",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }

            }
        }
    }
    }
}

private fun matchFilter(item: InboxItem, filter: LibFilter): Boolean = when (filter) {
    LibFilter.ALL -> true
    // 待学习：已分析、未点「开始学习」、未掌握
    LibFilter.TO_LEARN ->
        item.card.status == CardStatus.ANALYZED && item.card.dueAt == null && !item.card.mastered
    LibFilter.MASTERED -> item.card.mastered
    LibFilter.PENDING -> item.card.status != CardStatus.ANALYZED
}

@Composable
private fun SearchField(query: String, onSearch: () -> Unit, onChange: (String) -> Unit) {
    BasicTextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        modifier = Modifier.fillMaxWidth()
            .padding(top = 16.dp, bottom = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        decorationBox = { inner ->
            if (query.isEmpty()) {
                Text("搜索句子 / 单词 / 编号…", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            inner()
        },
    )
}

@Composable
private fun FilterChip(label: String, selected: Boolean, count: Int = 0, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            "$label${if (count > 0) " $count" else ""}",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun LibRow(
    item: InboxItem,
    reviewCount: Int,
    query: String,
    batchRunning: Boolean,
    onClick: () -> Unit,
    reanalyzing: Boolean,
    onReanalyze: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    CardSurface(onClick = onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // 固定编号：Card.id（AUTOINCREMENT 永不复用），与复习页/详情页同一编号，可互相对照。
            Text(
                "#${item.card.id}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Muted,
                modifier = Modifier.padding(end = 8.dp),
            )
            Spacer(Modifier.weight(1f))
            // 角标：批量分析中 → 未完成的统一显示「分析中…」（后台实际逐个进行，先到先亮）；
            // 否则分析未完成 → 实际状态；已分析未学习 → 待学习；已学习 → 已分析。
            when {
                batchRunning && item.card.status != CardStatus.ANALYZED -> StatusChip(CardStatus.ANALYZING)
                item.card.status != CardStatus.ANALYZED -> StatusChip(item.card.status)
                item.card.dueAt == null && !item.card.mastered -> LearnChip()
                else -> StatusChip(CardStatus.ANALYZED)
            }
            // 右上角三点菜单：删除入口（点删除弹确认框，确定后才删）。
            // 自绘小按钮替代 IconButton（48dp 最小点击区过高），压紧头部行让位给句子。
            Box {
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp))
                        .clickable { menuOpen = true }
                        .padding(4.dp),
                ) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "更多操作",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    shape = RoundedCornerShape(14.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp,
                ) {
                    DropdownMenuItem(
                        text = {
                            Text("重新分析", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        enabled = !reanalyzing,
                        onClick = {
                            menuOpen = false
                            onReanalyze()
                        },
                        modifier = Modifier.width(132.dp),
                    )
                    DropdownMenuItem(
                        text = {
                            Text("删除", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Red)
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                tint = Red,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = {
                            menuOpen = false
                            confirmDelete = true
                        },
                        modifier = Modifier.width(132.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        // 高亮：句子命中 → 命中段；句子未命中但重点词命中 → 高亮该重点词在句中的位置。
        val hitRanges = if (query.isNotBlank()) {
            SearchMatcher.match(query, item.card.text)?.ranges
                ?: item.words.firstNotNullOfOrNull { matched ->
                    SearchMatcher.match(query, matched.word)?.let { SearchMatcher.match(matched.word, item.card.text)?.ranges }
                }
        } else null
        if (hitRanges != null && hitRanges.isNotEmpty()) {
            HighlightedSentence(item.card.text, hitRanges)
        } else {
            androidx.compose.material3.Text(item.card.text, style = SentenceType)
        }
        if (item.words.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            // FlowRow：词条过长时换行，避免横向溢出裁切
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item.words.take(5).forEach { w ->
                    Text(
                        w.word,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Green,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Green.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // 来源移到卡片底部：长来源不再占头部三行，格式「分享: <来源>」。
        // 旧数据来源可能已带「分享: 」前缀（系统剪贴板标签），显示时去重。
        MetaLine("分享: ${item.card.source.removePrefix("分享:").removePrefix("分享：").trimStart()}")
        Spacer(Modifier.height(2.dp))
        val state = when {
            item.card.mastered -> "已掌握"
            reviewCount > 0 -> "已复习 $reviewCount 次"
            else -> "未开始"
        }
        MetaLine("${item.card.createdAt.toDateTimeString()} · $state")
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除卡片") },
            text = { Text("确定要删除这张卡片吗？删除后可在底部提示中撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("删除", color = Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}

/** 句子文本 + 命中段高亮（命中区间来自词级匹配：整词/整词组）。 */
@Composable
private fun HighlightedSentence(text: String, ranges: List<IntRange>) {
    val primary = MaterialTheme.colorScheme.primary
    val annotated = remember(text, ranges) {
        buildAnnotatedString {
            append(text)
            ranges.forEach { r ->
                if (r.first in text.indices && r.last in text.indices) {
                    addStyle(
                        SpanStyle(
                            color = primary,
                            fontWeight = FontWeight.Bold,
                            background = primary.copy(alpha = 0.15f),
                        ),
                        r.first, r.last + 1,
                    )
                }
            }
        }
    }
    Text(annotated, style = SentenceType)
}

private val DATE_TIME = java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss")

/** 采集时间：`2026.08.16 18:14:05`（日期带上时分秒）。 */
private fun Long.toDateTimeString(): String =
    java.time.Instant.ofEpochMilli(this)
        .atZone(java.time.ZoneId.systemDefault())
        .format(DATE_TIME)

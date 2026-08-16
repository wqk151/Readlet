package com.readlet.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.readlet.app.data.db.Card
import com.readlet.app.data.db.CardStatus
import com.readlet.app.data.db.CardWord
import com.readlet.app.ui.AppViewModel
import com.readlet.app.ui.Keywords
import com.readlet.app.ui.SentenceText
import com.readlet.app.ui.StatusChip
import com.readlet.app.ui.theme.Amber
import com.readlet.app.ui.theme.Blue
import com.readlet.app.ui.theme.Green
import com.readlet.app.ui.theme.Muted

/** 卡片详情：全屏覆盖页。译文 + 重点词 + 要点 + 语法 + 搭配 + 来源。 */
@Composable
fun CardDetailScreen(vm: AppViewModel, cardId: Long, backLabel: String = "← 返回收件箱") {
    // 实时观察卡片：重新分析后状态/内容变化即时反映（不再是一次性快照）。
    val cardFlow = remember(cardId) { vm.observeCard(cardId) }
    val card by cardFlow.collectAsStateWithLifecycle(initialValue = null)
    // 防重复点击：该卡手动重新分析中时禁用按钮。
    val analyzing = vm.detailAnalyzing.collectAsStateWithLifecycle().value.contains(cardId)
    var words by remember { mutableStateOf<List<CardWord>>(emptyList()) }

    // 进入即加载词表；分析完成后（含重新分析）重新加载。
    LaunchedEffect(card?.status) {
        if (card?.status == CardStatus.ANALYZED) {
            words = vm.loadWords(cardId)
        }
    }

    val c = card
    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            backLabel,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.closeDetail() }
                .padding(vertical = 6.dp),
        )
        Spacer(Modifier.height(8.dp))

        if (c == null) {
            Text("加载中…", color = Muted)
            return@Column
        }

        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp),
        ) {
            SentenceText(c.text, medium = true)
            Spacer(Modifier.height(8.dp))
            c.translation?.let {
                Text(it, fontSize = 15.sp, lineHeight = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(c.status)
                if (c.mastered) {
                    Text(
                        "已掌握",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Amber,
                        modifier = Modifier.padding(start = 8.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Amber.copy(alpha = 0.12f))
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                    )
                }
            }

            if (c.status == CardStatus.ANALYZED) {
                if (words.isNotEmpty()) {
                    Section("🔑", "重点单词") {
                        words.forEach { w ->
                            // 词性统一在单词后：LLM 词性优先，缺失时从释义前缀拆出（旧数据兼容）；
                            // 释义渲染前剥掉词性前缀——词性绝不出现第二处（翻译行）。
                            val split = remember(w) { Keywords.splitPos(w.meaningInContext ?: "") }
                            val pos = w.pos ?: split.first
                            val meaning = split.second
                            // 原型显示在音标行：`/luːmd/ · 原型 loom`，无音标时该行只显示原型。
                            val lemma = remember(w) { vm.lemmaOf(w.word) }
                            // 词+词性一行、音标行（含原型）、级别标记在音标下一行、释义紧随其后：
                            // 长词组音标不再被挤进窄列堆叠，与释义间也不留空白。
                            Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(w.word, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Green)
                                    pos?.let {
                                        Text(" $it", fontSize = 12.sp, color = Blue)
                                    }
                                }
                                val phonetic = w.phonetic?.takeIf { it.isNotBlank() }
                                if (phonetic != null || lemma != null) {
                                    Text(
                                        listOfNotNull(phonetic, lemma?.let { "原型 $it" }).joinToString(" · "),
                                        fontSize = 12.sp,
                                        color = Muted,
                                        modifier = Modifier.padding(top = 1.dp),
                                    )
                                }
                                // 级别标记在音标下一行（无音标则在词行下方），无级别不显示。
                                // 字体与音标行一致（12sp Muted 无背景），避免标签式强调抢视线。
                                Keywords.levelLabel(w.level)?.let { lvl ->
                                    Text(
                                        lvl,
                                        fontSize = 12.sp,
                                        color = Muted,
                                        modifier = Modifier.padding(top = 1.dp),
                                    )
                                }
                                meaning.takeIf { it.isNotBlank() }?.let { m ->
                                    Text(
                                        m,
                                        fontSize = 13.sp,
                                        lineHeight = 20.sp,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                val points = vm.decodePairs(c.pointsJson)
                if (points.isNotEmpty()) {
                    Section("💡", "要点") {
                        points.forEach { (expr, meaning) ->
                            Column(Modifier.padding(vertical = 4.dp)) {
                                Text("「$expr」", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(meaning, fontSize = 13.sp, lineHeight = 20.sp)
                            }
                        }
                    }
                }
                val grammar = vm.decodePairs(c.grammarJson)
                if (grammar.isNotEmpty()) {
                    Section("🧩", "语法分析") {
                        grammar.forEach { (structure, explanation) ->
                            Column(Modifier.padding(vertical = 4.dp)) {
                                Text("$structure", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Green)
                                Text(explanation, fontSize = 13.sp, lineHeight = 20.sp)
                            }
                        }
                    }
                }
                val collocations = vm.decodeCollocations(c.collocationsJson)
                if (collocations.isNotEmpty()) {
                    Section("🔗", "搭配积累") {
                        collocations.forEach { row ->
                            Column(Modifier.padding(vertical = 4.dp)) {
                                Text(
                                    "${row.getOrElse(0) { "" }}：${row.getOrElse(1) { "" }}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                row.getOrNull(2)?.takeIf { it.isNotBlank() }?.let {
                                    Text("例句：$it", fontSize = 13.sp)
                                }
                                row.getOrNull(3)?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, fontSize = 12.sp, color = Muted)
                                }
                            }
                        }
                    }
                }
            } else {
                Text(
                    if (c.status == CardStatus.FAILED)
                        "分析失败：可点击下方重新分析（请检查 API Key 设置与网络）"
                    else
                        "正在分析中，联网后自动完成。",
                    fontSize = 13.sp,
                    color = Muted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
            // 操作按钮靠右对齐；来源与时间单独一行（不再与按钮挤在同一行）。
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    when {
                        c.mastered -> ActionButton("取消已掌握", Amber) { vm.unmasterCard(c.id) }
                        c.status == CardStatus.ANALYZED && c.dueAt == null ->
                            ActionButton("开始学习（明天起进入复习排程）", Green) { vm.learnCard(c.id) }
                        c.status == CardStatus.FAILED || c.status == CardStatus.PENDING ->
                            // 分析中禁用按钮，防重复点击
                            ActionButton(
                                if (analyzing) "分析中…" else "重新分析",
                                Blue,
                                enabled = !analyzing,
                            ) { vm.reanalyze(c.id) }
                    }
                }
                // 学习门槛提示：学习 = 通读本页；未学习的卡不会出现在复习里。
                if (c.status == CardStatus.ANALYZED && c.dueAt == null) {
                    Text(
                        "学习 = 通读本页译文与重点词；未学习的卡不会出现在复习里",
                        fontSize = 11.sp,
                        color = Muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "${c.source} · ${c.createdAt.toLocalString()}",
                    fontSize = 11.sp,
                    color = Muted,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Section(icon: String, title: String, content: @Composable () -> Unit) {
    Row(
        Modifier.padding(top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 13.sp)
        Spacer(Modifier.width(6.dp))
        Text(
            title,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    content()
}

@Composable
private fun ActionButton(label: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (enabled) color else color.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

private fun Long.toLocalString(): String {
    val dt = java.time.Instant.ofEpochMilli(this)
        .atZone(java.time.ZoneId.systemDefault())
    val now = java.time.LocalDateTime.now()
    return if (dt.toLocalDate() == now.toLocalDate())
        "今天 ${String.format("%02d:%02d", dt.hour, dt.minute)}"
    else "${dt.monthValue}/${dt.dayOfMonth} ${String.format("%02d:%02d", dt.hour, dt.minute)}"
}

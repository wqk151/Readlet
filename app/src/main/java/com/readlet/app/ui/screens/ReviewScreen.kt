package com.readlet.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readlet.app.data.db.Card
import com.readlet.app.data.db.CardWord
import com.readlet.app.ui.AppViewModel
import com.readlet.app.ui.EmptyHint
import com.readlet.app.ui.KeywordMetaLine
import com.readlet.app.ui.Keywords
import com.readlet.app.ui.SentenceText
import com.readlet.app.ui.theme.Amber
import com.readlet.app.ui.theme.Blue
import com.readlet.app.ui.theme.Green
import com.readlet.app.ui.theme.HighlightOrange
import com.readlet.app.ui.theme.Muted
import com.readlet.app.ui.theme.Red
import com.readlet.app.ui.theme.SentenceType as SentenceTextStyle

/**
 * 复习：进度环 + 闪卡（正面句子/背面完整分析）+ 四键评级；加练模式优先。
 * 队列未加载完时显示「加载中…」而非空态（避免首帧空态 → 衬线句子的字体跳变）；
 * 重点词由 ViewModel 预载，首帧即带高亮渲染。
 */
@Composable
fun ReviewScreen(vm: AppViewModel) {
    val session by vm.reviewSession.collectAsStateWithLifecycle()
    val words by vm.reviewWords.collectAsStateWithLifecycle()
    val seqOf by vm.cardSeq.collectAsStateWithLifecycle()
    var flipped by remember { mutableStateOf(false) }

    val card = session.card
    LaunchedEffect(card?.id) { flipped = false }

    // 答案面按返回键 → 翻回题目（再按一次才是退出）
    BackHandler(enabled = flipped) { flipped = false }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ReviewHead(session)
        Spacer(Modifier.height(14.dp))

        when {
            card == null && !session.ready -> {
                // 加载中：直接以衬线字体占位（队列加载完成前不渲染空态，
                // 避免默认字体 → 衬线的整体跳变）。
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("加载中…", style = SentenceTextStyle, color = Muted)
                }
            }
            card == null -> {
                EmptyHint("今日复习完成 🎉", "到期卡片已清空。\n去卡片库拾取新句子，或稍后再来。")
            }
            else -> Flashcard(
                card = card,
                words = words,
                seq = seqOf[card.id] ?: 0,
                flipped = flipped,
                isPractice = session.isPractice,
                lemmaOf = { vm.lemmaOf(it) },
                onClickFlip = { flipped = !flipped }, // 正面翻到答案；答案面「返回题目」翻回
                onGrade = { q ->
                    vm.grade(q)
                    flipped = false
                },
                onMaster = { vm.masterCard(card.id) },
            )
        }
    }
}

@Composable
private fun ReviewHead(session: com.readlet.app.ui.ReviewSession) {
    val total = session.remaining
    val done = session.doneToday
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val progress = if (total + done > 0) done.toFloat() / (total + done) else 0f
        Box(
            Modifier.size(64.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(4.dp, MaterialTheme.colorScheme.primary, CircleShape)
                .padding(2.dp)
                .clip(CircleShape)
                .background(if (progress > 0f) Green.copy(alpha = 0.25f) else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text("$done/$total", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            if (session.isPractice) {
                Text(
                    "加练中 · 剩余 ${session.remaining}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Blue,
                )
            }
            Text(
                "今日待复习 ${session.dueCount}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "今日已学 $done",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "只含你加入复习的卡片",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Flashcard(
    card: Card,
    words: List<CardWord>,
    seq: Int,
    flipped: Boolean,
    isPractice: Boolean,
    lemmaOf: (String) -> String?,
    onClickFlip: () -> Unit,
    onGrade: (Int) -> Unit,
    onMaster: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 全局序号：与卡片库同一编号，可互相对照。
        if (seq > 0) {
            Text(
                "#$seq · 已学 ${card.reps} 次",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Muted,
                modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp),
            )
        }
        if (flipped) {
            // 背面：译文 + 重点词 + 语法（可滚动，长句不裁剪）
            Column(
                Modifier.weight(1f).fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "← 返回题目",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Start)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onClickFlip() }
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                )
                card.translation?.let {
                    Text(
                        it,
                        fontSize = 15.sp,
                        lineHeight = 24.sp,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    )
                }
                if (words.isNotEmpty()) {
                    words.forEach { w ->
                        // 词性统一在单词后：LLM 词性优先，缺失时从释义前缀拆出（旧数据兼容）；
                        // 释义渲染前剥掉词性前缀——词性绝不出现第二处（翻译行）。
                        val split = remember(w) { Keywords.splitPos(w.meaningInContext ?: "") }
                        val pos = w.pos ?: split.first
                        val meaning = split.second
                            // 原型：存储值（LLM 提供 / 分析时词表兜底）优先，旧数据渲染时词表查表兜底。
                            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        w.word,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = HighlightOrange,
                                    )
                                    pos?.let { p ->
                                        Text(p, fontSize = 12.sp, color = Blue, modifier = Modifier.padding(start = 6.dp))
                                    }
                                }
                                KeywordMetaLine(
                                    word = w.word,
                                    phonetic = w.phonetic,
                                    phoneticUs = w.phoneticUs,
                                    level = w.level,
                                    lemma = w.lemma ?: lemmaOf(w.word),
                                )
                                meaning.takeIf { it.isNotBlank() }?.let { m ->
                                    Text(
                                        m,
                                        fontSize = 13.sp,
                                        lineHeight = 20.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                            }
                    }
                } else {
                    Text("（本句为单词/短语卡，见上）", fontSize = 12.sp, color = Muted)
                }
            }
            Spacer(Modifier.height(16.dp))
            GradeRow { onGrade(it) }
        } else {
            // 正面：句子 + 全部重点词/词组高亮（可滚动，长句完整显示）
            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 24.dp),
            ) {
                SentenceText(card.text, large = true, highlightWords = words.map { it.word })
            }
            if (isPractice) {
                Text("加练卡片 · 不改变排程", fontSize = 12.sp, color = Muted)
            }
            Spacer(Modifier.height(12.dp))
            // 「显示答案」与「标记已掌握」并排居中。
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onClickFlip)
                        .padding(horizontal = 34.dp, vertical = 11.dp),
                ) {
                    Text(
                        "显示答案",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp))
                        .background(Amber)
                        .clickable(onClick = onMaster)
                        .padding(horizontal = 20.dp, vertical = 11.dp),
                ) {
                    Text(
                        "标记已掌握",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

/** 四键评级：重来=1 困难=3 良好=4 简单=5（SM-2 quality）。 */
@Composable
private fun GradeRow(onGrade: (Int) -> Unit) {
    val grades = listOf(
        1 to "重来" to Red,
        3 to "困难" to Amber,
        4 to "良好" to Green,
        5 to "简单" to Blue,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        grades.forEach { (g, color) ->
            val (q, label) = g
            Box(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color)
                    .clickable { onGrade(q) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

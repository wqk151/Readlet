package com.readlet.app.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readlet.app.ui.AppViewModel
import com.readlet.app.ui.KnowledgeDoc
import com.readlet.app.ui.SectionTitle
import com.readlet.app.ui.theme.Amber
import com.readlet.app.ui.theme.Green
import com.readlet.app.ui.theme.Muted
import androidx.compose.ui.graphics.toArgb
import kotlin.math.roundToInt

/** 统计：月度概览 + 热力图 + 曲线 + 高频难点词榜。 */
@Composable
fun StatsScreen(vm: AppViewModel) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    val counts by vm.dailyCounts.collectAsStateWithLifecycle()
    val total by vm.totalCards.collectAsStateWithLifecycle()
    val llmCalls by vm.llmCallCount.collectAsStateWithLifecycle()
    val llmTokens by vm.llmTotalTokens.collectAsStateWithLifecycle()

    val weeks = remember(counts) { vm.heatmapWeeks(counts) }
    val recent = remember(counts) { vm.recentCounts(counts, 30) }
    val month = remember(counts) { vm.monthStats(counts) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        StatCard(month.first, month.second, total, stats.distinctWords, llmCalls, llmTokens)
        SectionTitle("学习日历 · 近 12 周")
        Heatmap(weeks, vm.heatmapMonths(), vm.heatmapToday())
        SectionTitle("近 30 天复习量")
        LineChart(recent.map { it.second.toFloat() }, label = { i -> vm.formatDay(recent[i].first) })
        SectionTitle("高频难点词")
        StatsLinkRow(
            title = "高频难点词 · 按出现频率",
            subtitle = if (stats.wordFreq.isEmpty()) "分析句子后，这里会汇总反复出现的高频难点词。" else "共 ${stats.wordFreq.size} 个",
            onClick = { vm.openDifficultyWords() },
        )
        SectionTitle("词根库")
        StatsLinkRow(
            title = "词根库 · 按词族规模",
            subtitle = "共 ${vm.roots.allRoots().size} 个词根",
            onClick = { vm.openRootLibrary() },
        )
        SectionTitle("发音规律")
        StatsLinkRow(
            title = "发音规律 · 语流音变速查",
            subtitle = "浊化/同化/连读/省音/弱读/缩读等 12 章规则速查",
            onClick = { vm.openKnowledge(KnowledgeDoc.PRON_RULES) },
        )
        SectionTitle("音标")
        StatsLinkRow(
            title = "国际音标 · 48 个",
            subtitle = "元音 20 + 辅音 28 · 发音技巧与样例",
            onClick = { vm.openKnowledge(KnowledgeDoc.IPA) },
        )
        Spacer(Modifier.height(24.dp))
    }
}

/** 概览：两行 x 3 项。词/词组 = 已分析卡片中出现的去重词数；大模型调用与 Token 来自 llm_usage 表。 */
@Composable
private fun StatCard(
    monthDays: Int,
    rate: Int,
    totalCards: Int,
    distinctWords: Int,
    llmCalls: Int,
    llmTokens: Int,
) {
    val rows = listOf(
        listOf("$monthDays" to "本月学习天数", "$rate%" to "打卡率", "$totalCards" to "总卡片"),
        listOf("$distinctWords" to "词/词组", "$llmCalls" to "大模型调用", "$llmTokens" to "Token 用量"),
    )
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 12.dp),
    ) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                row.forEach { (v, l) ->
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(v, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        Text(l, fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
    }
}

/** GitHub 式热力图：12 列 x 7 行。带月份轴、星期轴、今日标记与图例。 */
@Composable
private fun Heatmap(weeks: List<List<Int>>, months: List<Pair<Int, String>>, today: Pair<Int, Int>) {
    val shape = RoundedCornerShape(14.dp)
    // 月份标签落在所属连续列的中间列，居中显示。
    val monthCenters = months.map { (col, label) ->
        val end = months.firstOrNull { it.first > col }?.first?.minus(1) ?: 11
        (col + end) / 2 to label
    }
    Column(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
    ) {
        // 月份轴：左侧与星期列同宽留白，列宽分布与数据行一致。
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Spacer(Modifier.width(22.dp))
            weeks.indices.forEach { col ->
                Box(Modifier.weight(1f).height(16.dp), contentAlignment = Alignment.Center) {
                    monthCenters.firstOrNull { it.first == col }?.let { (_, label) ->
                        Text(label, fontSize = 10.sp, color = Muted, maxLines = 1)
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        // 数据网格：行=周一~周日，列=周（列 0 最旧，列 11 本周）。
        (0..6).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                // 星期轴：只标 一/三/五/日，与对应行对齐。
                Box(Modifier.width(22.dp).height(16.dp), contentAlignment = Alignment.Center) {
                    if (row == 0 || row == 2 || row == 4 || row == 6) {
                        Text(listOf("一", "三", "五", "日")[row / 2], fontSize = 9.sp, color = Muted)
                    }
                }
                weeks.forEachIndexed { col, week ->
                    val cnt = week[row]
                    val isToday = today.first == col && today.second == row
                    Box(
                        Modifier.weight(1f).height(16.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(heatColor(heatLevel(cnt)))
                            .then(
                                if (isToday) Modifier.border(1.5.dp, Amber, RoundedCornerShape(3.dp))
                                else Modifier,
                            ),
                    )
                }
            }
            if (row < 6) Spacer(Modifier.height(3.dp))
        }
        Spacer(Modifier.height(8.dp))
        // 图例：少 → 多。
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("少", fontSize = 10.sp, color = Muted)
            Spacer(Modifier.width(6.dp))
            (0..4).forEach { level ->
                Box(
                    Modifier.width(10.dp).height(10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(heatColor(level)),
                )
                if (level < 4) Spacer(Modifier.width(3.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text("多", fontSize = 10.sp, color = Muted)
        }
    }
}

/** 复习量 → 色阶 0..4（0=无记录）。 */
private fun heatLevel(cnt: Int): Int = when {
    cnt <= 0 -> 0
    cnt <= 2 -> 1
    cnt <= 5 -> 2
    cnt <= 10 -> 3
    else -> 4
}

/** 色阶 → 颜色：空=面底色，1..4 逐级加深主绿。 */
@Composable
private fun heatColor(level: Int): Color = when (level) {
    1 -> Green.copy(alpha = 0.25f)
    2 -> Green.copy(alpha = 0.5f)
    3 -> Green.copy(alpha = 0.75f)
    4 -> Green
    else -> MaterialTheme.colorScheme.surfaceVariant
}

/** 折线图（Canvas 手绘，无图表库）。卡片全宽与其他区块右对齐；刻度在卡片内右侧留白列，不占绘图区。 */
@Composable
private fun LineChart(values: List<Float>, label: (Int) -> String) {
    if (values.size < 2) return
    val max = values.maxOrNull()?.coerceAtLeast(1f) ?: 1f
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    // Y 轴刻度值：0 / 中值 / 最大值（小数域取整档位）。
    val tickValues = when {
        max <= 1f -> listOf(0f, max)
        max <= 2f -> listOf(0f, 1f, max)
        else -> listOf(0f, max / 2f, max)
    }.distinct()
    Column {
        Box(
            Modifier.fillMaxWidth().height(120.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(10.dp),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val plotTop = 4.dp.toPx()
                val plotBottom = size.height - 4.dp.toPx()
                val tickWidth = 34.dp.toPx()
                val plotRight = size.width - tickWidth
                val yFor: (Float) -> Float = { v -> plotBottom - (v / max) * (plotBottom - plotTop) }

                // 刻度网格线（数值在右侧刻度列绘制，不占绘图区）。
                tickValues.forEach { v ->
                    val y = yFor(v)
                    drawLine(
                        gridColor,
                        Offset(0f, y),
                        Offset(plotRight, y),
                        strokeWidth = 1f,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                    )
                }

                    val pts = values.mapIndexed { i, v ->
                        Offset(
                            x = plotRight * i / (values.size - 1),
                            y = yFor(v),
                        )
                    }
            val line = Path().apply {
                moveTo(pts.first().x, pts.first().y)
                pts.drop(1).forEach { lineTo(it.x, it.y) }
            }
            val area = Path().apply {
                addPath(line)
                lineTo(pts.last().x, size.height)
                lineTo(pts.first().x, size.height)
                close()
            }
                    drawPath(area, Green.copy(alpha = 0.10f))
                    drawPath(line, Green, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                    pts.forEach { drawCircle(Green, 2.5.dp.toPx(), it) }

                    // 右侧刻度数值：右对齐到卡片内边缘（plotRight 之后），与网格线同一 y，不覆盖折线区域。
                    val textPaint = android.graphics.Paint().apply {
                        color = Muted.toArgb()
                        textSize = 9.sp.toPx()
                        textAlign = android.graphics.Paint.Align.RIGHT
                        isAntiAlias = true
                    }
                    tickValues.forEach { v ->
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawText(
                                v.roundToInt().toString(),
                                size.width - 4.dp.toPx(),
                                yFor(v) + textPaint.textSize * 0.35f,
                                textPaint,
                            )
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp).padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label(0), fontSize = 10.sp, color = Muted)
            Text(label(values.size - 1), fontSize = 10.sp, color = Muted)
        }
}

/** 统计页 `>` 入口行：标题 + 副标题（计数/说明）+ 右侧箭头，点进对应分页列表页。 */
@Composable
private fun StatsLinkRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Green)
            if (subtitle.isNotEmpty()) Text(subtitle, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 2.dp))
        }
        Text("›", fontSize = 20.sp, color = Muted, modifier = Modifier.padding(start = 8.dp))
    }
}

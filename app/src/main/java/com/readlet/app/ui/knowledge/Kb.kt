package com.readlet.app.ui.knowledge

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.readlet.app.ui.CardSurface
import com.readlet.app.ui.theme.Muted

/**
 * 发音知识库静态内容模型（随包发布，纯参考展示，无 Room 变更）。
 * 单元格内用 `**…**` 标记加粗（源自原文档的强调，如 **re**sign 中的音变字母）。
 */

/** 单个知识块：说明 / 分组小标题 / 提醒 / 表格 / 等宽文本块。 */
sealed interface KbBlock {
    /** 章节导语或规则说明段。 */
    data class Lead(val text: String) : KbBlock

    /** 分组小标题（如「2.1 顺向同化」「10.1 /h/ 省略」）。 */
    data class Sub(val text: String) : KbBlock

    /** ⚠️/💡 提醒。 */
    data class Tip(val text: String) : KbBlock

    /** 表格：w 为各列权重（与 head/每行单元格一一对应）。firstBold=首列加粗（音标列）。 */
    data class Table(
        val head: List<String>?,
        val w: List<Float>,
        val rows: List<List<String>>,
        val firstBold: Boolean = false,
    ) : KbBlock

    /** 等宽块（速记口诀 / 分类层级图等），超宽可横向滚动。 */
    data class Code(val lines: List<String>) : KbBlock
}

/** 知识章节：标题 + 自上而下的一组知识块。 */
data class KbSection(val title: String, val body: List<KbBlock>)

/** `**…**` 标记解析为加粗的富文本。 */
private fun rich(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val s = text.indexOf("**", i)
        if (s < 0) {
            append(text.substring(i))
            break
        }
        append(text.substring(i, s))
        val e = text.indexOf("**", s + 2)
        if (e < 0) {
            append(text.substring(s))
            break
        }
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(s + 2, e)) }
        i = e + 2
    }
}

/** 单个知识块的渲染。 */
@Composable
private fun KbBlockView(block: KbBlock) {
    when (block) {
        is KbBlock.Lead -> Text(
            rich(block.text),
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        is KbBlock.Sub -> Text(
            block.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Muted,
            letterSpacing = 0.4.sp,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        is KbBlock.Tip -> Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                rich(block.text),
                fontSize = 12.5.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        is KbBlock.Table -> KbTableView(block)

        is KbBlock.Code -> {
            val scroll = rememberScrollState()
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .horizontalScroll(scroll)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                block.lines.forEach { line ->
                    Text(
                        line,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun KbTableView(table: KbBlock.Table) {
    val hairline = MaterialTheme.colorScheme.outline
    Column(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp)) {
        if (table.head != null) {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                table.head.forEachIndexed { i, h ->
                    Text(
                        h,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Muted,
                        letterSpacing = 0.3.sp,
                        modifier = Modifier.weight(table.w[i]).padding(end = if (i < table.w.size - 1) 10.dp else 0.dp),
                    )
                }
            }
            HorizontalDivider(color = hairline, thickness = 1.dp)
        }
        table.rows.forEachIndexed { r, cells ->
            Row(
                Modifier.fillMaxWidth()
                    .padding(vertical = 6.dp),
            ) {
                cells.forEachIndexed { i, cell ->
                    Text(
                        rich(cell),
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        fontWeight = if (i == 0 && table.firstBold) FontWeight.Bold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(table.w[i]).padding(end = if (i < table.w.size - 1) 10.dp else 0.dp),
                    )
                }
            }
            if (r < table.rows.size - 1) {
                HorizontalDivider(color = hairline, thickness = 0.6.dp)
            }
        }
    }
}

/** 知识章节卡片：章节标题 + 全部知识块。 */
@Composable
fun KbSectionCard(section: KbSection) {
    CardSurface {
        Text(
            section.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        section.body.forEach { block -> KbBlockView(block) }
    }
}

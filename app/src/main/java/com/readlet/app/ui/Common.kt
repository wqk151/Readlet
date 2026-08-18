package com.readlet.app.ui

import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.readlet.app.data.db.CardStatus
import com.readlet.app.ui.theme.Amber
import com.readlet.app.ui.theme.Blue
import com.readlet.app.ui.theme.Green
import com.readlet.app.ui.theme.HighlightOrange
import com.readlet.app.ui.theme.Muted
import com.readlet.app.ui.theme.Red
import com.readlet.app.ui.theme.SentenceLarge
import com.readlet.app.ui.theme.SentenceMedium
import com.readlet.app.ui.theme.SentenceType

/** 卡片状态三态 chip：已分析=绿 / 分析中=蓝 / 待分析=红 / 失败=红。 */
@Composable
fun StatusChip(status: Int) {
    val (bg, fg, label) = when (status) {
        CardStatus.ANALYZED -> Triple(Green.copy(alpha = 0.12f), Green, "已分析")
        CardStatus.ANALYZING -> Triple(Blue.copy(alpha = 0.12f), Blue, "分析中…")
        CardStatus.FAILED -> Triple(Red.copy(alpha = 0.12f), Red, "分析失败")
        else -> Triple(Red.copy(alpha = 0.12f), Red, "待分析")
    }
    Text(
        label,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** 「待学习」chip（方案 A）：已分析但未点「开始学习」，不进复习队列。 */
@Composable
fun LearnChip() {
    Text(
        "待学习",
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = Amber,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Amber.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** 句子正文（衬线）。large=复习页大字；medium=详情页略缩。highlightWords=句中高亮的重点词/词组。 */
@Composable
fun SentenceText(text: String, large: Boolean = false, medium: Boolean = false, highlightWords: List<String> = emptyList()) {
    val base = when {
        large -> SentenceLarge
        medium -> SentenceMedium
        else -> SentenceType
    }
    if (highlightWords.isEmpty()) {
        Text(text, style = base)
    } else {
        // 简单高亮：命中词/词组变色加粗。用单个 Text + AnnotatedString，
        // 避免 Row 拼接多段 Text 时高亮词在句中/句尾导致剩余文本被挤成窄列而显示不全。
        // 词组按长度降序优先匹配（"look forward to" 先于 "look"），
        // findAll 顺序扫描天然不重叠，无需额外去重。
        // 淡橙高亮：复习页重点词与主绿操作按钮区分开。
        val highlight = HighlightOrange
        val annotated = remember(text, highlightWords, highlight) {
            buildAnnotatedString {
                append(text)
                highlightRegex(highlightWords)?.findAll(text)?.forEach { m ->
                    addStyle(
                        SpanStyle(color = highlight, fontWeight = FontWeight.Bold),
                        m.range.first, m.range.last + 1,
                    )
                }
            }
        }
        Text(annotated, style = base)
    }
}

/** 高亮匹配正则：词组按长度降序优先匹配，findAll 顺序扫描天然不重叠；无关键词时返回 null。 */
private fun highlightRegex(highlightWords: List<String>): Regex? {
    val keywords = highlightWords.mapNotNull { it.trim().takeIf(String::isNotBlank) }
        .distinct()
        .sortedByDescending { it.length }
    if (keywords.isEmpty()) return null
    return Regex("(?i)\\b(${keywords.joinToString("|") { Regex.escape(it) }})\\b")
}

/** 原生 TextView 版句子：与 SentenceText 同一高亮匹配，供复习正面划词渲染（系统划词工具栏）。 */
fun sentenceSpannable(text: String, highlightWords: List<String>): SpannableString {
    val spannable = SpannableString(text)
    highlightRegex(highlightWords)?.findAll(text)?.forEach { m ->
        spannable.setSpan(
            ForegroundColorSpan(HighlightOrange.toArgb()),
            m.range.first, m.range.last + 1,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        spannable.setSpan(
            StyleSpan(android.graphics.Typeface.BOLD),
            m.range.first, m.range.last + 1,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }
    return spannable
}

/** 区块标题。 */
@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

/** 空态提示。 */
@Composable
fun EmptyHint(title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(6.dp))
        Text(
            body, fontSize = 13.sp, lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** 元信息行：来源 · 时间 · 次数。 */
@Composable
fun MetaLine(content: String) {
    Text(
        content,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 重点词元信息行：英/美音标 · 原型 · 级别，全部中点号隔开（级别与音标同行）；缺项不显示。 */
@Composable
fun KeywordMetaLine(
    word: String,
    phonetic: String?,
    phoneticUs: String?,
    level: String?,
    lemma: String?,
) {
    // 与词形相同的原型没有展示价值（harbor → 原型 harbor），隐藏；blank 同样隐藏。
    val lemmaShown = lemma?.takeIf { it.isNotBlank() && !it.equals(word, ignoreCase = true) }
    val parts = listOfNotNull(
        phonetic?.takeIf { it.isNotBlank() }?.let { "英 $it" },
        phoneticUs?.takeIf { it.isNotBlank() }?.let { "美 $it" },
        lemmaShown?.let { "原型 $it" },
        Keywords.levelLabel(level),
    )
    if (parts.isNotEmpty()) {
        Text(
            parts.joinToString(" · "),
            fontSize = 12.sp,
            color = Muted,
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}

/** 白色圆角卡片容器。内容自上而下排列（Column）。 */
@Composable
fun CardSurface(onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val base = Modifier.fillMaxWidth()
        .clip(shape)
        .background(MaterialTheme.colorScheme.surface)
        .padding(14.dp)
    val clickable = if (onClick != null) base.clickable(onClick = onClick) else base
    Column(clickable, content = content)
}

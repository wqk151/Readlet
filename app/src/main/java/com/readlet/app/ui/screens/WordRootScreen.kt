package com.readlet.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.readlet.app.data.Roots
import com.readlet.app.llm.EtymologyItem
import com.readlet.app.ui.AppViewModel
import com.readlet.app.ui.theme.Amber
import com.readlet.app.ui.theme.Green
import com.readlet.app.ui.theme.Ink
import com.readlet.app.ui.theme.Muted

/** 词根页（自主设计，参考 Etyma 词根页风格）：
 * header=`port ＝ carry` + 来源 + 词族规模；词族行=词（绿）+ 拆解（前缀/后缀琥珀、词根绿，各附简要义）居左，
 * 释义/推导义居右；紧凑、细分割线。优先读词源库（LLM 生成的中文短义 + 推导义），缺则回退本地拆解+同根。 */
@Composable
fun WordRootScreen(vm: AppViewModel, rootName: String) {
    val entry = remember(rootName) { vm.roots.byRoot(rootName) }
    var etItems by remember { mutableStateOf<List<EtymologyItem>?>(null) }
    var seenCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    LaunchedEffect(rootName) {
        seenCounts = vm.loadSeenCounts()
        // 词源库优先；未命中则触发生成（幂等，失败返回 null → 回退本地）。
        val items = vm.loadEtymologyItems(rootName) ?: vm.ensureEtymology(rootName)?.items
        etItems = items
    }

    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            "← 返回",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.closeRoot() }
                .padding(vertical = 6.dp),
        )
        Spacer(Modifier.height(8.dp))

        if (entry == null) {
            Text("未找到该词根。", color = Muted, modifier = Modifier.padding(top = 16.dp))
            return@Column
        }

        // header：`port ＝ carry`（词根＝构词义），来源在右上，词族规模在下一行。
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(entry.root, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Green)
            Text(" ＝ ${entry.meaning}", fontSize = 19.sp, fontWeight = FontWeight.Medium, color = Ink)
            Spacer(Modifier.weight(1f))
            entry.origin?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 12.sp, color = Muted) }
        }
        Text(
            "词族 ${etItems?.size ?: entry.family.size} 词",
            fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

        // 词源库条目（LLM：中文短义 + 推导义），否则回退本地拆解/同根。
        val shown = etItems
        if (shown == null) {
            entry.family.forEach { w ->
                val seen = seenCounts[w.lowercase()] ?: 0
                LocalFamilyRow(
                    word = w,
                    breakdown = vm.roots.breakdownOf(w),
                    gloss = vm.dictionaryGloss(w),
                    rootMeaning = entry.meaning,
                    seen = seen,
                    onClick = if (seen > 0) { { vm.openWord(w) } } else null,
                )
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }
        } else {
            shown.forEach { item ->
                val seen = seenCounts[item.word.lowercase()] ?: 0
                EtymFamilyRow(
                    item = item,
                    seen = seen,
                    onClick = if (seen > 0) { { vm.openWord(item.word) } } else null,
                )
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** 词族行：内部三行——①单词本身 ②拆分（按类型着色+中文义）③记忆翻译（推导义/中文释义）；命中词库可点。 */
@Composable
private fun FamilyRow(
    word: String,
    breakdown: List<Triple<String, String, String>>?,
    meaning: String?,
    seen: Int,
    onClick: (() -> Unit)?,
) {
    val base = Modifier.fillMaxWidth().padding(vertical = 10.dp).padding(start = 4.dp)
    Column(if (onClick != null) base.clickable(onClick = onClick) else base) {
        // ① 单词本身（右上角据「你见过 N 次」）
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(word, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Green)
            if (seen > 0) {
                Spacer(Modifier.weight(1f))
                Text("你见过 ${seen} 次", fontSize = 11.sp, color = Amber)
            }
        }
        // ② 拆分
        breakdown?.let { b ->
            Text(
                breakdownAnnotated(b),
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        // ③ 记忆翻译
        meaning?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
                lineHeight = 19.sp,
            )
        }
    }
}

/** LLM 词源条目行：bsk=前缀/词根/后缀 + 中文短义；meaning=推导义（`搬出去 → 出口`）。 */
@Composable
private fun EtymFamilyRow(item: EtymologyItem, seen: Int, onClick: (() -> Unit)?) {
    FamilyRow(
        word = item.word,
        breakdown = item.breakdown?.map { Triple(it.part, it.type, it.meaning) },
        meaning = item.meaning,
        seen = seen,
        onClick = onClick,
    )
}

/** 本地回退行：breakdown=本地推导（英文首义），meaning=词典义/同根。 */
@Composable
private fun LocalFamilyRow(
    word: String,
    breakdown: List<Roots.AffixPart>?,
    gloss: String?,
    rootMeaning: String,
    seen: Int,
    onClick: (() -> Unit)?,
) {
    FamilyRow(
        word = word,
        breakdown = breakdown?.map { Triple(it.part, it.type, shortMeaning(it.meaning)) },
        meaning = gloss ?: rootMeaning.takeIf { it.isNotBlank() }?.let { "同根 $it" },
        seen = seen,
        onClick = onClick,
    )
}

/** 拆解渲染：前缀/后缀=琥珀、词根=绿，各构件后跟简要义；义为空则只显构件。 */
private fun breakdownAnnotated(parts: List<Triple<String, String, String>>): androidx.compose.ui.text.AnnotatedString =
    buildAnnotatedString {
        parts.forEachIndexed { i, (part, type, meaning) ->
            if (i > 0) withStyle(SpanStyle(color = Muted, fontSize = 12.sp)) { append(" ＋ ") }
            val color = when (type) {
                "前缀", "后缀" -> Amber
                "词根" -> Green
                else -> Muted
            }
            withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)) { append(part) }
            if (meaning.isNotBlank()) withStyle(SpanStyle(color = color, fontSize = 12.sp)) { append("($meaning)") }
        }
    }

/** 构件简要义：取构词义的首义项（首个逗号/分号前）；过长则只保留前 4 词（不产生省略号）。 */
private fun shortMeaning(meaning: String): String {
    val first = meaning.split(',', ';', '；', '，', '\n').firstOrNull()?.trim().orEmpty()
    if (first.isEmpty()) return ""
    val words = first.split(' ')
    return if (words.size > 4) words.take(4).joinToString(" ") else first
}

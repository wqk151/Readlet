package com.readlet.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.readlet.app.ui.AppViewModel
import com.readlet.app.ui.KnowledgeDoc
import com.readlet.app.ui.knowledge.IpaSections
import com.readlet.app.ui.knowledge.KbSection
import com.readlet.app.ui.knowledge.KbSectionCard
import com.readlet.app.ui.knowledge.PronRulesSections
import com.readlet.app.ui.theme.Muted

private data class KnowledgePage(
    val meta: String,
    val intro: String,
    val sections: List<KbSection>,
)

private val pages = mapOf(
    KnowledgeDoc.PRON_RULES to KnowledgePage(
        meta = "发音规律 · ${PronRulesSections.size} 章",
        intro = "以下规则均为英语自然语流中的音系变化（phonological changes），并非拼写规则。",
        sections = PronRulesSections,
    ),
    KnowledgeDoc.IPA to KnowledgePage(
        meta = "国际音标 · 48 音",
        intro = "基于英式音标（DJ 音标）整理，涵盖全部 48 个音标的发音技巧与样例。",
        sections = IpaSections,
    ),
)

/** 发音知识库全屏页（统计页 `>` 入口进入）：发音规律 / 国际音标静态参考内容，随包发布。 */
@Composable
fun KnowledgeScreen(vm: AppViewModel, doc: KnowledgeDoc) {
    val page = pages.getValue(doc)
    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "← 返回",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.closeKnowledge() }
                    .padding(vertical = 6.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(page.meta, fontSize = 13.sp, color = Muted)
        }
        Text(
            page.intro,
            fontSize = 12.5.sp,
            lineHeight = 19.sp,
            color = Muted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
        ) {
            items(page.sections, key = { it.title }) { section ->
                KbSectionCard(section)
            }
        }
    }
}

package com.readlet.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.readlet.app.data.db.Card
import com.readlet.app.ui.AppViewModel
import com.readlet.app.ui.SentenceText
import com.readlet.app.ui.theme.Blue
import com.readlet.app.ui.theme.Green
import com.readlet.app.ui.theme.Muted

/** 词频详情：该词出现的所有句子 → 一键加入加练（不计入 SRS 排程）。 */
@Composable
fun WordDetailScreen(vm: AppViewModel, word: String) {
    var cards by remember { mutableStateOf<List<Card>>(emptyList()) }

    LaunchedEffect(word) {
        cards = vm.loadCardsByWord(word)
    }

    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            "← 返回统计",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.closeWord() }
                .padding(vertical = 6.dp),
        )
        Spacer(Modifier.height(8.dp))

        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable { vm.startPractice(word) }
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "把包含 $word 的 ${cards.size} 句加入加练",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Text(
            "加练不计入 SRS 排程，不影响原有复习计划",
            fontSize = 11.sp,
            color = Muted,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        if (cards.isEmpty()) {
            Text("该词暂无句子。", color = Muted, modifier = Modifier.padding(top = 16.dp))
        } else {
            cards.forEach { card ->
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { vm.openDetail(card.id) }
                        .padding(14.dp),
                ) {
                    SentenceText(card.text, highlightWords = listOf(word))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${card.source} · ${card.createdAt.toLocalString()}",
                        fontSize = 11.sp,
                        color = Muted,
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun Long.toLocalString(): String {
    val dt = java.time.Instant.ofEpochMilli(this)
        .atZone(java.time.ZoneId.systemDefault())
    val now = java.time.LocalDateTime.now()
    return if (dt.toLocalDate() == now.toLocalDate())
        "今天 ${String.format("%02d:%02d", dt.hour, dt.minute)}"
    else "${dt.monthValue}/${dt.dayOfMonth}"
}

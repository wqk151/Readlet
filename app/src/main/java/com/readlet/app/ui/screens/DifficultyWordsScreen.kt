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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.itemsIndexed
import com.readlet.app.data.db.WordFreq
import com.readlet.app.ui.AppViewModel
import com.readlet.app.ui.theme.Green
import com.readlet.app.ui.theme.Muted
import androidx.compose.runtime.snapshotFlow

private const val PAGE = 30

/** 高频难点词全量分页列表（统计页 `>` 入口进入）：按出现频率降序，无限滚动，页大小 30。
 * 每词行进词频详情（可加练）。 */
@Composable
fun DifficultyWordsScreen(vm: AppViewModel) {
    var items by remember { mutableStateOf<List<WordFreq>>(emptyList()) }
    LaunchedEffect(Unit) { items = vm.loadWordFreq() }

    val listState = rememberLazyListState()
    var shown by remember { mutableStateOf(PAGE) }
    val total = items.size
    LaunchedEffect(listState, items) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (last >= shown - 3 && shown < total) shown = (shown + PAGE).coerceAtMost(total) }
    }

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
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.closeDifficultyWords() }
                    .padding(vertical = 6.dp),
            )
            Spacer(Modifier.weight(1f))
            Text("高频难点词 · ${total}", fontSize = 13.sp, color = Muted)
        }

        if (items.isEmpty()) {
            Text(
                "分析句子后，这里会汇总反复出现的高频难点词。",
                fontSize = 13.sp, color = Muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        } else {
            LazyColumn(state = listState, modifier = Modifier.padding(horizontal = 16.dp)) {
                itemsIndexed(items.take(shown)) { _, f ->
                    WordFreqRow(f) { vm.openWord(f.word) }
                }
                if (shown < total) {
                    item { Text("加载中…", fontSize = 12.sp, color = Muted, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                }
            }
        }
    }
}

@Composable
private fun WordFreqRow(f: WordFreq, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(f.word, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Green,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("出现 ${f.freq} 次 · ${f.cards} 句", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 2.dp))
        }
        Text("›", fontSize = 18.sp, color = Muted)
    }
}

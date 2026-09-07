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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.readlet.app.data.Roots
import com.readlet.app.ui.AppViewModel
import com.readlet.app.ui.Keywords
import com.readlet.app.ui.theme.Green
import com.readlet.app.ui.theme.Muted

private const val PAGE = 30

/** 词根库（统计页 `>` 入口进入）：全部词根（仅 class=word根）按词族规模降序，顶部搜索（词根/含义），分页 30。
 * 点行进对应词根页看词族。 */
@Composable
fun RootsLibraryScreen(vm: AppViewModel) {
    val allRoots = remember { vm.roots.allRoots() }
    var query by remember { mutableStateOf("") }
    val filtered = remember(allRoots, query) {
        val q = query.trim()
        if (q.isEmpty()) allRoots
        else allRoots.filter { it.root.contains(q, ignoreCase = true) || it.meaning.contains(q, ignoreCase = true) }
    }

    val listState = rememberLazyListState()
    var shown by remember { mutableStateOf(PAGE) }
    val total = filtered.size
    LaunchedEffect(listState, filtered) {
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
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.closeRootLibrary() }
                    .padding(vertical = 6.dp),
            )
            Spacer(Modifier.weight(1f))
            Text("词根库 · $total", fontSize = 13.sp, color = Muted)
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("搜索词根 / 含义", fontSize = 14.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )

        if (filtered.isEmpty()) {
            Text("无匹配词根。", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        } else {
            LazyColumn(state = listState, modifier = Modifier.padding(horizontal = 16.dp)) {
                items(count = filtered.take(shown).size, key = { filtered[it].root }) { i ->
                    RootRow(filtered[i]) { vm.openRoot(filtered[i].root) }
                }
                if (shown < total) {
                    item { Text("加载中…", fontSize = 12.sp, color = Muted, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                }
            }
        }
    }
}

@Composable
private fun RootRow(e: Roots.RootEntry, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.root, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Green,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(" · ${e.family.size} 词", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(start = 6.dp))
            }
            val meta = listOfNotNull(
                (e.meaningZh?.takeIf { it.isNotBlank() } ?: e.meaning).takeIf { it.isNotBlank() },
                Keywords.originLabel(e.origin)?.let { "来源 $it" },
            ).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("›", fontSize = 18.sp, color = Muted)
    }
}

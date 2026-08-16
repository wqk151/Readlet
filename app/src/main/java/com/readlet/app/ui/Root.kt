package com.readlet.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readlet.app.ui.screens.CardDetailScreen
import com.readlet.app.ui.screens.LibraryScreen
import com.readlet.app.ui.screens.ReviewScreen
import com.readlet.app.ui.screens.SettingsDialog
import com.readlet.app.ui.screens.StatsScreen
import com.readlet.app.ui.screens.WordDetailScreen

/** 应用根：Scaffold + 底部导航 + 全屏覆盖页 + 返回键栈。 */
@Composable
fun Root(vm: AppViewModel) {
    val tab by vm.tab.collectAsStateWithLifecycle()
    val detailCardId by vm.detailCardId.collectAsStateWithLifecycle()
    val detailWord by vm.detailWord.collectAsStateWithLifecycle()
    val settingsOpen by vm.settingsOpen.collectAsStateWithLifecycle()
    val toast by vm.toast.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(toast) {
        toast?.let {
            val result = snackbarHostState.showSnackbar(
                it.text,
                actionLabel = if (it.undo) "撤销" else null,
                duration = if (it.undo) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) vm.undoDelete()
            vm.dismissToast()
        }
    }

    // 返回键优先级：设置 > 词频详情 > 卡片详情
    BackHandler(enabled = settingsOpen) { vm.settingsOpen.value = false }
    BackHandler(enabled = detailWord != null) { vm.closeWord() }
    BackHandler(enabled = detailCardId != null) { vm.closeDetail() }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = { BottomNav(tab, vm::selectTab) },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    Tab.Library -> LibraryScreen(vm)
                    Tab.Review -> ReviewScreen(vm)
                    Tab.Stats -> StatsScreen(vm)
                }
            }
        }

        // 详情页/设置覆盖全屏，返回文案按进入时的 Tab 区分（覆盖层在导航之上，Tab 不会中途变化）。
        detailCardId?.let { CardDetailScreen(vm, it, backLabelFor(tab)) }
        detailWord?.let { WordDetailScreen(vm, it) }
        if (settingsOpen) SettingsDialog(vm)

        // Snackbar 置于覆盖层之上：全屏详情页打开时 toast 仍可见。
        SnackbarHost(
            snackbarHostState,
            Modifier.align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        )
    }
}

private fun backLabelFor(tab: Tab): String = when (tab) {
    Tab.Library -> "← 返回卡片库"
    Tab.Review -> "← 返回复习"
    Tab.Stats -> "← 返回统计"
}

@Composable
private fun BottomNav(current: Tab, onSelect: (Tab) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Row(
            Modifier.fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 1.dp),
        ) {
            Tab.entries.forEach { t ->
                val selected = t == current
                Column(
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else Color.Transparent
                        )
                        .clickable { onSelect(t) }
                        .padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(t.icon, fontSize = 16.sp)
                    Text(
                        t.label,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

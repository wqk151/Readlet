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
import com.readlet.app.ui.screens.VoiceSettingsDialog
import com.readlet.app.ui.screens.StatsScreen
import com.readlet.app.ui.screens.WordDetailScreen
import com.readlet.app.ui.screens.WordRootScreen
import com.readlet.app.ui.screens.DifficultyWordsScreen
import com.readlet.app.ui.screens.RootsLibraryScreen

/** 应用根：Scaffold + 底部导航 + 全屏覆盖页 + 返回键栈。 */
@Composable
fun Root(vm: AppViewModel) {
    val tab by vm.tab.collectAsStateWithLifecycle()
    val overlays by vm.overlays.collectAsStateWithLifecycle()
    val settingsOpen by vm.settingsOpen.collectAsStateWithLifecycle()
    val voiceSettingsOpen by vm.voiceSettingsOpen.collectAsStateWithLifecycle()
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

    // 返回键：对话框最上；覆盖页栈只关栈顶（后进先出，解决 card→rootPage、rootPage→word、word→card 的循环）。
    BackHandler(enabled = voiceSettingsOpen) { vm.closeVoiceSettings() }
    BackHandler(enabled = settingsOpen) { vm.settingsOpen.value = false }
    BackHandler(enabled = overlays.isNotEmpty()) {
        when (val top = overlays.last()) {
            is Overlay.CardDetail -> vm.closeDetail()
            is Overlay.WordDetail -> vm.closeWord()
            is Overlay.RootPage -> vm.closeRoot()
            Overlay.DifficultyWords -> vm.closeDifficultyWords()
            Overlay.RootLibrary -> vm.closeRootLibrary()
        }
    }

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

        // 覆盖页：只渲染栈顶（被盖住的下一层在关闭栈顶后经重组恢复），返回文案按进入时的 Tab 区分。
        when (val top = overlays.lastOrNull()) {
            is Overlay.CardDetail -> CardDetailScreen(vm, top.cardId, backLabelFor(tab))
            is Overlay.WordDetail -> WordDetailScreen(vm, top.word)
            is Overlay.RootPage -> WordRootScreen(vm, top.root)
            Overlay.DifficultyWords -> DifficultyWordsScreen(vm)
            Overlay.RootLibrary -> RootsLibraryScreen(vm)
            null -> {}
        }
        if (settingsOpen) SettingsDialog(vm)
        // 发音设置由主设置入口打开，叠在其上（两个 AlertDialog 同屏允许）。
        if (voiceSettingsOpen) VoiceSettingsDialog(vm)

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

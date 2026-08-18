package com.readlet.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.readlet.app.ui.AppViewModel
import com.readlet.app.ui.Root
import com.readlet.app.ui.theme.ReadletTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    /** 前台标记：划词工具栏里点 Readlet 自己时判定为「划词查库」而非外部「划词采集」。 */
    private var foreground = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm.onAppStart()
        handleIntent(intent)
        setContent {
            ReadletTheme {
                Root(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        foreground = true
    }

    override fun onPause() {
        foreground = false
        super.onPause()
    }

    /** 分享/文本选择进入（App 已在运行：singleTask 走 onNewIntent）。 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * 分享/划词进入：外部来源静默采集后立刻退回后台，不打断原 app 的阅读；
     * 前台自来源（复习正面划词点工具栏里的 Readlet）→ 划词查库，打开词频详情，不采集、不退回。
     */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        var handled = false
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
                vm.onShared(text, sourceLabel(intent))
                handled = true
            }
            Intent.ACTION_PROCESS_TEXT -> {
                val text = intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT) ?: return
                if (foreground) {
                    // 划词查库：Readlet 在前台时收到 PROCESS_TEXT，来源只可能是自己的划词工具栏
                    vm.openWord(text.trim())
                } else {
                    vm.onShared(text, sourceLabel(intent))
                    handled = true
                }
            }
        }
        // 静默采集：入库与异步分析在后台完成，用户停留在分享来源的 app。
        if (handled) moveTaskToBack(true)
    }

    /**
     * 来源：优先 EXTRA_TITLE/SUBJECT（阅读 app 分享时往往是书名/文章标题）
     * → 应用显示名（referrer → callingPackage）→ 剪贴板标签 → 降级「系统分享」。
     * 单独用 callingPackage 在多数分享场景下为 null，所以来源常被记成「系统分享」。
     */
    private fun sourceLabel(intent: Intent): String {
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
        listOf(Intent.EXTRA_TITLE, Intent.EXTRA_SUBJECT).forEach { key ->
            intent.getStringExtra(key)?.trim()?.takeIf { it.isNotBlank() && it != sharedText }
                ?.let { return stripSharePrefix(it) }
        }
        return stripSharePrefix(callingAppLabel(intent))
    }

    /** 剪贴板标签常带系统前缀「分享: <标题>」，剥掉再入库（展示层统一拼「分享: xxx」）。 */
    private fun stripSharePrefix(label: String): String {
        val s = label.trimStart()
            .removePrefix("分享:").removePrefix("分享：").trim()
        return s.ifBlank { "系统分享" }
    }

    /** 来源 App 显示名，失败降级。 */
    private fun callingAppLabel(intent: Intent): String {
        // getReferrer（Activity 级）比 callingPackage 更可靠：分享/文本选择都常带来源包名。
        val pkg = try {
            getReferrer()?.host?.takeIf { it.isNotBlank() } ?: callingPackage
        } catch (_: Exception) {
            callingPackage
        }
        if (!pkg.isNullOrBlank()) {
            try {
                val pm = packageManager
                return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (_: Exception) {
                return pkg
            }
        }
        intent.clipData?.description?.label?.takeIf { it.isNotBlank() }?.let { return it.toString() }
        return "系统分享"
    }
}

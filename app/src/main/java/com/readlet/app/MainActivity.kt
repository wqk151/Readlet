package com.readlet.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
        // 分享成功的 heads-up 提示需要通知权限（Android 13+），首次启动请求一次。
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
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
     * 划词进入：外部来源静默采集后立刻退回后台，不打断原 app 的阅读；
     * 前台自来源（复习正面划词点工具栏里的 Readlet）→ 划词查库，打开词频详情，不采集、不退回。
     * （分享接收已移到 ShareReceiverActivity：透明幽灵页静默入库，不显示 Readlet 窗口。）
     */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        if (intent.action != Intent.ACTION_PROCESS_TEXT) return
        val text = intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT) ?: return
        if (foreground) {
            // 划词查库：Readlet 在前台时收到 PROCESS_TEXT，来源只可能是自己的划词工具栏
            vm.openWord(text.trim())
        } else {
            // 静默采集：入库在后台完成，用户停留在划词来源的 app。
            vm.onShared(text, ShareSource.label(this, intent))
            moveTaskToBack(true)
        }
    }
}

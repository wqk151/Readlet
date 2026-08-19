package com.readlet.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import com.readlet.app.ui.AppViewModel

/**
 * 分享接收「幽灵」Activity：透明无动画窗口，接住 ACTION_SEND 静默入库后立即退回原 app，
 * 全程不显示 Readlet 界面（用户停留在来源 app，仅系统 Toast 提示成功）。
 * 独立 taskAffinity + excludeFromRecents：分享进单独任务，不会把主任务带到前台造成「切过来又切回去」的闪现；
 * 入库完成后 finish 自身，不残留后台任务。
 */
class ShareReceiverActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    /** 快速连续分享时系统可能复用本接收任务（onNewIntent 而非新建实例）。 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (intent?.action != Intent.ACTION_SEND || text.isNullOrBlank()) {
            finish()
            return
        }
        // 入库与 Toast 在后台协程完成（viewModelScope 随本页存续），完成后回调 finish 自清。
        vm.onShared(text, ShareSource.label(this, intent)) { finish() }
        moveTaskToBack(true)
    }
}

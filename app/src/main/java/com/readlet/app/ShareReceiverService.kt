package com.readlet.app

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.readlet.app.data.repo.CardRepository
import kotlinx.coroutines.launch

/**
 * 分享接收服务（Android 11+「分享到服务」目标）：系统在后台绑定/启动本服务并投递 ACTION_SEND，
 * 不创建任何 Activity/任务 → 来源 app 全程留在前台，分享零切换，仅弹系统 Toast 提示。
 * 入库在进程级 appScope 完成：服务被系统解绑销毁后也不中断。
 * API 30 以下系统不把服务列为分享目标，由 ShareReceiverActivity（幽灵页）回退。
 */
class ShareReceiverService : Service() {

    private val repo: CardRepository get() = (application as ReadletApp).repository

    private val binder = object : Binder() {}

    override fun onBind(intent: Intent): IBinder {
        accept(intent)
        return binder
    }

    /** 兜底：部分实现以 start 方式投递而非绑定。 */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) accept(intent)
        return START_NOT_STICKY
    }

    private fun accept(intent: Intent) {
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (text.isNullOrBlank()) return
        (application as ReadletApp).appScope.launch {
            val (_, added) = repo.acceptShared(text, ShareSource.labelForService(this@ShareReceiverService, intent))
            ShareFeedback.notify(applicationContext, added)
        }
    }
}

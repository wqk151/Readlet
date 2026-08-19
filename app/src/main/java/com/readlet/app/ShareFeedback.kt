package com.readlet.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * 分享成功提示：优先 heads-up 通知——后台可显示、不被 ROM 压制；
 * 通知权限被关时降级系统 Toast。两者都只短暂出现在当前前台 app 之上，不跳转。
 */
object ShareFeedback {

    private const val CHANNEL_ID = "share_received"
    private const val CHANNEL_NAME = "分享接收"
    private const val NOTIFY_ID = 1001

    fun notify(context: Context, added: Boolean) {
        val text = if (added) "已收录到 Readlet，可稍后手动分析" else "该句子已在 Readlet 卡片库中"
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            ensureChannel(context)
            NotificationManagerCompat.from(context).notify(NOTIFY_ID, notification(context, text))
        } else {
            Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show()
        }
    }

    private fun notification(context: Context, text: String): android.app.Notification {
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("拾句")
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
        // 3 秒后自动消失，不堆积通知栏（API 30+ 平台支持；更低版本由用户点掉/滑动掉）。
        if (Build.VERSION.SDK_INT >= 30) builder.setTimeoutAfter(3_000)
        return builder.build()
    }

    /** 静音悬浮频道：HIGH 才弹 heads-up，但不出声、不震动，不打断阅读。 */
    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
        channel.setSound(null, null)
        channel.enableVibration(false)
        nm.createNotificationChannel(channel)
    }
}

package com.readlet.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 分享/划词来源解析：优先 EXTRA_TITLE/SUBJECT（阅读 app 分享时往往是书名/文章标题）
 * → 应用显示名（referrer → callingPackage）→ 剪贴板标签 → 降级「系统分享」。
 * 单独用 callingPackage 在多数分享场景下为 null，所以来源常被记成「系统分享」。
 * Activity 路径（MainActivity 划词 / ShareReceiverActivity）与 Service 路径（ShareReceiverService）共用。
 */
object ShareSource {

    /** Activity 路径：Activity.getReferrer 比 callingPackage 更可靠：分享/文本选择都常带来源包名。 */
    fun label(activity: Activity, intent: Intent): String {
        val referrerHost = try { activity.getReferrer()?.host } catch (_: Exception) { null }
        return label(activity, referrerHost, activity.callingPackage, intent)
    }

    /** Service 路径：系统只在启动 Activity 时写 referrer/callingPackage，服务收不到 → 降级 EXTRA_REFERRER_NAME。 */
    fun labelForService(context: Context, intent: Intent): String {
        val referrerHost = intent.getStringExtra(Intent.EXTRA_REFERRER_NAME)
            ?.let { runCatching { Uri.parse(it).host }.getOrNull() }
        return label(context, referrerHost, null, intent)
    }

    private fun label(context: Context, referrerHost: String?, callingPkg: String?, intent: Intent): String {
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
        listOf(Intent.EXTRA_TITLE, Intent.EXTRA_SUBJECT).forEach { key ->
            intent.getStringExtra(key)?.trim()?.takeIf { it.isNotBlank() && it != sharedText }
                ?.let { return stripSharePrefix(it) }
        }
        return stripSharePrefix(callingAppLabel(context, referrerHost, callingPkg, intent))
    }

    /** 剪贴板标签常带系统前缀「分享: <标题>」，剥掉再入库（展示层统一拼「分享: xxx」）。 */
    private fun stripSharePrefix(label: String): String {
        val s = label.trimStart()
            .removePrefix("分享:").removePrefix("分享：").trim()
        return s.ifBlank { "系统分享" }
    }

    /** 来源 App 显示名，失败降级。 */
    private fun callingAppLabel(context: Context, referrerHost: String?, callingPkg: String?, intent: Intent): String {
        val pkg = referrerHost?.takeIf { it.isNotBlank() } ?: callingPkg
        if (!pkg.isNullOrBlank()) {
            try {
                val pm = context.packageManager
                return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (_: Exception) {
                return pkg
            }
        }
        intent.clipData?.description?.label?.takeIf { it.isNotBlank() }?.let { return it.toString() }
        return "系统分享"
    }
}

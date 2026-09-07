package com.readlet.app

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import com.readlet.app.data.Settings
import com.readlet.app.data.Roots
import com.readlet.app.data.WordLevels
import com.readlet.app.data.db.AppDatabase
import com.readlet.app.data.repo.CardRepository
import com.readlet.app.tts.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ReadletApp : Application() {

    lateinit var db: AppDatabase
        private set
    lateinit var settings: Settings
        private set
    lateinit var wordLevels: WordLevels
        private set
    lateinit var roots: Roots
        private set
    lateinit var repository: CardRepository
        private set
    lateinit var tts: TtsManager
        private set

    /** 进程级协程作用域：分享服务等无 Activity 宿主的地方做后台入库用，随进程存续，不被组件销毁取消。 */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.build(this)
        settings = Settings(this)
        wordLevels = WordLevels.load(this)
        roots = Roots.load(this)
        repository = CardRepository(this, db, settings, wordLevels, roots, appScope)
        // 发音子系统单例：懒加载引擎/语音包，随进程存续。
        tts = TtsManager(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 分享目标固定走 ShareReceiverService（后台绑定，来源 app 零切换）；
            // 幽灵 Activity 仅作 API 30 以下回退，禁用避免分享面板列出两个「拾句」入口。
            packageManager.setComponentEnabledSetting(
                ComponentName(this, ShareReceiverActivity::class.java),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}

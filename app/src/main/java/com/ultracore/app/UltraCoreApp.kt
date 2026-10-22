package com.ultracore.app

import android.app.Application
import com.ultracore.app.shizuku.ShizukuManager

class UltraCoreApp : Application() {
    override fun onCreate() {
        super.onCreate()
        com.ultracore.app.ui.theme.ThemePrefs.init(this)
        ShizukuManager.init(this)
        com.ultracore.app.manager.AppChangeStore.init(this)
        com.ultracore.app.sandbox.SandboxManager.init(this)
    }
}

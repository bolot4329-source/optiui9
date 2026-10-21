package com.ultracore.app

import android.app.Application
import com.ultracore.app.shizuku.ShizukuManager

class UltraCoreApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ShizukuManager.init(this)
    }
}

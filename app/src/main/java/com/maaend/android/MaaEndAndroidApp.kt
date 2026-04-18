package com.maaend.android

import android.app.Application
import com.maaend.android.root.RootManager

class MaaEndAndroidApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RootManager.initialize(this)
    }
}

package com.maaend.android

import android.app.Application
import android.util.Log
import com.maaend.android.bridge.DriverClass
import com.maaend.android.root.RootManager

class MaaEndAndroidApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DriverClass.installContext(this)
        runCatching {
            RootManager.initialize(this)
        }.onFailure { error ->
            Log.e(TAG, "Failed to initialize root manager", error)
        }
    }

    private companion object {
        const val TAG = "MaaEndAndroidApp"
    }
}

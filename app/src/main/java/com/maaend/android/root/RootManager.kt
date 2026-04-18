package com.maaend.android.root

import android.content.Context
import com.maaend.android.BuildConfig
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object RootManager {
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) {
            return
        }
        initialized = true
        Shell.enableVerboseLogging = BuildConfig.DEBUG
        @Suppress("DEPRECATION")
        Shell.setDefaultBuilder(
            Shell.Builder.create().setFlags(Shell.FLAG_REDIRECT_STDERR),
        )
    }

    fun isGranted(): Boolean {
        return runCatching {
            Shell.isAppGrantedRoot() == true || Shell.getCachedShell()?.isRoot == true
        }.getOrDefault(false)
    }

    fun isAvailable(): Boolean {
        if (isGranted()) {
            return true
        }
        val execPaths = System.getenv("PATH")?.split(":") ?: return false
        return execPaths.any { File(it, "su").canExecute() }
    }

    suspend fun requestPermission(): Boolean = withContext(Dispatchers.IO) {
        runCatching { Shell.getShell().isRoot }.getOrDefault(false)
    }
}

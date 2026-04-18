package com.maaend.android.storage

import android.content.Context

data class AppSettings(
    val lastSelectedTaskId: String? = null,
    val lastSelectedPresetId: String? = null,
    val logLevel: String = "info",
)

class AppSettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("maaend_android_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings {
        return AppSettings(
            lastSelectedTaskId = prefs.getString(KEY_LAST_TASK_ID, null),
            lastSelectedPresetId = prefs.getString(KEY_LAST_PRESET_ID, null),
            logLevel = prefs.getString(KEY_LOG_LEVEL, "info") ?: "info",
        )
    }

    fun saveLastTaskId(taskId: String?) {
        prefs.edit().putString(KEY_LAST_TASK_ID, taskId).apply()
    }

    fun saveLastPresetId(presetId: String?) {
        prefs.edit().putString(KEY_LAST_PRESET_ID, presetId).apply()
    }

    fun saveLogLevel(logLevel: String) {
        prefs.edit().putString(KEY_LOG_LEVEL, logLevel).apply()
    }

    private companion object {
        const val KEY_LAST_TASK_ID = "last_task_id"
        const val KEY_LAST_PRESET_ID = "last_preset_id"
        const val KEY_LOG_LEVEL = "log_level"
    }
}

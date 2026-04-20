package com.maaend.android.storage

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class AppSettings(
    val lastSelectedTaskId: String? = null,
    val lastSelectedPresetId: String? = null,
    val logLevel: String = "info",
    val checkedTaskIds: Set<String> = emptySet(),
    val taskOptionSelectionsByTask: Map<String, Map<String, Set<String>>> = emptyMap(),
    val taskInputValuesByTask: Map<String, Map<String, Map<String, String>>> = emptyMap(),
)

class AppSettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("maaend_android_settings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(): AppSettings {
        return AppSettings(
            lastSelectedTaskId = prefs.getString(KEY_LAST_TASK_ID, null),
            lastSelectedPresetId = prefs.getString(KEY_LAST_PRESET_ID, null),
            logLevel = prefs.getString(KEY_LOG_LEVEL, "info") ?: "info",
            checkedTaskIds = decode(KEY_CHECKED_TASK_IDS, emptySet()),
            taskOptionSelectionsByTask = decode(KEY_TASK_OPTION_SELECTIONS, emptyMap()),
            taskInputValuesByTask = decode(KEY_TASK_INPUT_VALUES, emptyMap()),
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

    fun saveCheckedTaskIds(taskIds: Set<String>) {
        encode(KEY_CHECKED_TASK_IDS, taskIds)
    }

    fun saveTaskOptionSelectionsByTask(selections: Map<String, Map<String, Set<String>>>) {
        encode(KEY_TASK_OPTION_SELECTIONS, selections)
    }

    fun saveTaskInputValuesByTask(values: Map<String, Map<String, Map<String, String>>>) {
        encode(KEY_TASK_INPUT_VALUES, values)
    }

    private inline fun <reified T> decode(key: String, defaultValue: T): T {
        val raw = prefs.getString(key, null) ?: return defaultValue
        return runCatching { json.decodeFromString<T>(raw) }.getOrDefault(defaultValue)
    }

    private inline fun <reified T> encode(key: String, value: T) {
        prefs.edit().putString(key, json.encodeToString(value)).apply()
    }

    private companion object {
        const val KEY_LAST_TASK_ID = "last_task_id"
        const val KEY_LAST_PRESET_ID = "last_preset_id"
        const val KEY_LOG_LEVEL = "log_level"
        const val KEY_CHECKED_TASK_IDS = "checked_task_ids"
        const val KEY_TASK_OPTION_SELECTIONS = "task_option_selections"
        const val KEY_TASK_INPUT_VALUES = "task_input_values"
    }
}

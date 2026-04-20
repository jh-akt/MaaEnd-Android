package com.maaend.android.storage

import android.content.Context
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class AppSettingsBackup(
    val version: Int = 1,
    val exportedAt: Long = 0L,
    val settings: AppSettings = AppSettings(),
)

@Serializable
data class AppSettings(
    val lastSelectedTaskId: String? = null,
    val lastSelectedPresetId: String? = null,
    val selectedResourceId: String? = null,
    val logLevel: String = "info",
    val checkedTaskIds: Set<String> = emptySet(),
    val taskOptionSelectionsByTask: Map<String, Map<String, Set<String>>> = emptyMap(),
    val taskInputValuesByTask: Map<String, Map<String, Map<String, String>>> = emptyMap(),
    val sharedOptionSelectionsByScope: Map<String, Map<String, Set<String>>> = emptyMap(),
    val sharedInputValuesByScope: Map<String, Map<String, Map<String, String>>> = emptyMap(),
)

class AppSettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("maaend_android_settings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(): AppSettings {
        return AppSettings(
            lastSelectedTaskId = prefs.getString(KEY_LAST_TASK_ID, null),
            lastSelectedPresetId = prefs.getString(KEY_LAST_PRESET_ID, null),
            selectedResourceId = prefs.getString(KEY_SELECTED_RESOURCE_ID, null),
            logLevel = normalizeLogLevel(prefs.getString(KEY_LOG_LEVEL, "info")),
            checkedTaskIds = decode(KEY_CHECKED_TASK_IDS, emptySet()),
            taskOptionSelectionsByTask = decode(KEY_TASK_OPTION_SELECTIONS, emptyMap()),
            taskInputValuesByTask = decode(KEY_TASK_INPUT_VALUES, emptyMap()),
            sharedOptionSelectionsByScope = decode(KEY_SHARED_OPTION_SELECTIONS, emptyMap()),
            sharedInputValuesByScope = decode(KEY_SHARED_INPUT_VALUES, emptyMap()),
        )
    }

    fun saveLastTaskId(taskId: String?) {
        prefs.edit().putString(KEY_LAST_TASK_ID, taskId).apply()
    }

    fun saveLastPresetId(presetId: String?) {
        prefs.edit().putString(KEY_LAST_PRESET_ID, presetId).apply()
    }

    fun saveSelectedResourceId(resourceId: String?) {
        prefs.edit().putString(KEY_SELECTED_RESOURCE_ID, resourceId).apply()
    }

    fun saveLogLevel(logLevel: String) {
        prefs.edit().putString(KEY_LOG_LEVEL, normalizeLogLevel(logLevel)).apply()
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

    fun saveSharedOptionSelectionsByScope(selections: Map<String, Map<String, Set<String>>>) {
        encode(KEY_SHARED_OPTION_SELECTIONS, selections)
    }

    fun saveSharedInputValuesByScope(values: Map<String, Map<String, Map<String, String>>>) {
        encode(KEY_SHARED_INPUT_VALUES, values)
    }

    fun exportTo(outputStream: OutputStream) {
        val backup = AppSettingsBackup(
            exportedAt = System.currentTimeMillis(),
            settings = load(),
        )
        outputStream.bufferedWriter().use { writer ->
            writer.write(
                Json(json) {
                    prettyPrint = true
                }.encodeToString(backup),
            )
        }
    }

    fun importFrom(inputStream: InputStream): AppSettings {
        val backup = inputStream.bufferedReader().use { reader ->
            json.decodeFromString<AppSettingsBackup>(reader.readText())
        }
        require(backup.version <= BACKUP_VERSION) {
            "不支持的配置版本：${backup.version}"
        }
        replaceAll(backup.settings)
        return backup.settings
    }

    fun replaceAll(settings: AppSettings) {
        prefs.edit()
            .clear()
            .putString(KEY_LAST_TASK_ID, settings.lastSelectedTaskId)
            .putString(KEY_LAST_PRESET_ID, settings.lastSelectedPresetId)
            .putString(KEY_SELECTED_RESOURCE_ID, settings.selectedResourceId)
            .putString(KEY_LOG_LEVEL, normalizeLogLevel(settings.logLevel))
            .putString(KEY_CHECKED_TASK_IDS, json.encodeToString(settings.checkedTaskIds))
            .putString(KEY_TASK_OPTION_SELECTIONS, json.encodeToString(settings.taskOptionSelectionsByTask))
            .putString(KEY_TASK_INPUT_VALUES, json.encodeToString(settings.taskInputValuesByTask))
            .putString(KEY_SHARED_OPTION_SELECTIONS, json.encodeToString(settings.sharedOptionSelectionsByScope))
            .putString(KEY_SHARED_INPUT_VALUES, json.encodeToString(settings.sharedInputValuesByScope))
            .apply()
    }

    private inline fun <reified T> decode(key: String, defaultValue: T): T {
        val raw = prefs.getString(key, null) ?: return defaultValue
        return runCatching { json.decodeFromString<T>(raw) }.getOrDefault(defaultValue)
    }

    private inline fun <reified T> encode(key: String, value: T) {
        prefs.edit().putString(key, json.encodeToString(value)).apply()
    }

    private fun normalizeLogLevel(logLevel: String?): String {
        return when (logLevel?.lowercase()) {
            "error", "warn", "info", "debug" -> logLevel.lowercase()
            else -> "info"
        }
    }

    private companion object {
        const val BACKUP_VERSION = 1
        const val KEY_LAST_TASK_ID = "last_task_id"
        const val KEY_LAST_PRESET_ID = "last_preset_id"
        const val KEY_SELECTED_RESOURCE_ID = "selected_resource_id"
        const val KEY_LOG_LEVEL = "log_level"
        const val KEY_CHECKED_TASK_IDS = "checked_task_ids"
        const val KEY_TASK_OPTION_SELECTIONS = "task_option_selections"
        const val KEY_TASK_INPUT_VALUES = "task_input_values"
        const val KEY_SHARED_OPTION_SELECTIONS = "shared_option_selections"
        const val KEY_SHARED_INPUT_VALUES = "shared_input_values"
    }
}

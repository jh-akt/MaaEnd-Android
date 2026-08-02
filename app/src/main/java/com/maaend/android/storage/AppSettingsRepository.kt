package com.maaend.android.storage

import android.content.Context
import com.maaframework.android.host.MaaHostSettings
import com.maaframework.android.host.MaaHostSettingsBackup
import com.maaframework.android.host.MaaHostSettingsRepository
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.Serializable

typealias AppSettingsBackup = MaaHostSettingsBackup

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
    private val delegate = MaaHostSettingsRepository(
        context = context,
        preferencesName = "maaend_android_settings",
        projectId = "maaend",
    )

    fun load(): AppSettings = delegate.load().toAppSettings()
    fun saveLastTaskId(value: String?) = delegate.saveSelectedTaskId(value)
    fun saveLastPresetId(value: String?) = delegate.saveSelectedPresetId(value)
    fun saveSelectedResourceId(value: String?) = delegate.saveSelectedResourceId(value)
    fun saveLogLevel(value: String) = delegate.saveLogLevel(value)
    fun saveCheckedTaskIds(value: Set<String>) = delegate.saveCheckedTaskIds(value)
    fun saveTaskOptionSelectionsByTask(value: Map<String, Map<String, Set<String>>>) =
        delegate.saveTaskOptionSelectionsByTask(value)
    fun saveTaskInputValuesByTask(value: Map<String, Map<String, Map<String, String>>>) =
        delegate.saveTaskInputValuesByTask(value)
    fun saveSharedOptionSelectionsByScope(value: Map<String, Map<String, Set<String>>>) =
        delegate.saveSharedOptionSelectionsByScope(value)
    fun saveSharedInputValuesByScope(value: Map<String, Map<String, Map<String, String>>>) =
        delegate.saveSharedInputValuesByScope(value)
    fun exportTo(outputStream: OutputStream) = delegate.exportTo(outputStream)
    fun importFrom(inputStream: InputStream): AppSettings = delegate.importFrom(inputStream).toAppSettings()
    fun replaceAll(settings: AppSettings) = delegate.replaceAll(settings.toHostSettings())
}

private fun MaaHostSettings.toAppSettings() = AppSettings(
    lastSelectedTaskId = selectedTaskId,
    lastSelectedPresetId = selectedPresetId,
    selectedResourceId = selectedResourceId,
    logLevel = logLevel,
    checkedTaskIds = checkedTaskIds,
    taskOptionSelectionsByTask = taskOptionSelectionsByTask,
    taskInputValuesByTask = taskInputValuesByTask,
    sharedOptionSelectionsByScope = sharedOptionSelectionsByScope,
    sharedInputValuesByScope = sharedInputValuesByScope,
)

private fun AppSettings.toHostSettings() = MaaHostSettings(
    selectedResourceId = selectedResourceId,
    selectedTaskId = lastSelectedTaskId,
    selectedPresetId = lastSelectedPresetId,
    checkedTaskIds = checkedTaskIds,
    taskOptionSelectionsByTask = taskOptionSelectionsByTask,
    taskInputValuesByTask = taskInputValuesByTask,
    sharedOptionSelectionsByScope = sharedOptionSelectionsByScope,
    sharedInputValuesByScope = sharedInputValuesByScope,
    logLevel = logLevel,
)

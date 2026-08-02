package com.maaend.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.maaend.android.BuildConfig
import com.maaframework.android.host.MaaHostActions
import com.maaframework.android.host.MaaHostPolicies
import com.maaframework.android.host.MaaHostState
import com.maaframework.android.host.MaaHostTab
import com.maaframework.android.host.MaaHostUiConfig
import com.maaframework.android.model.RootEnvironmentReport
import com.maaframework.android.project.MaaProjectManifestLoader
import com.maaframework.android.ui.MaaHostApp

@Composable
fun MaaEndApp(viewModel: MainViewModel) {
    MaaEndTheme {
        val state by viewModel.uiState.collectAsState()
        val context = LocalContext.current
        val manifest = remember(context) { MaaProjectManifestLoader.loadOrDefault(context.assets) }
        val actions = remember(viewModel) { viewModel.hostActions() }
        MaaHostApp(
            state = state.toHostState(manifest),
            actions = actions,
            config = MaaHostUiConfig(
                appName = manifest.displayName,
                versionName = BuildConfig.VERSION_NAME,
                policy = MaaHostPolicies.MaaEnd,
                showSharedOptions = true,
                showManualOverride = false,
            ),
        )
    }
}

private fun MainUiState.toHostState(manifest: com.maaframework.android.project.MaaProjectManifest) = MaaHostState(
    activeTab = activeTab.toHostTab(),
    manifest = manifest,
    catalog = catalog,
    resourceRepository = resourceRepository,
    resourceRepositoryUpdating = resourceRepositoryUpdating,
    resourceRepositoryProgress = resourceRepositoryProgress,
    resourceRepositoryClearConfirmVisible = resourceRepositoryClearConfirmVisible,
    rootReport = RootEnvironmentReport(
        available = rootAvailable,
        granted = rootGranted,
        summary = lastMessage,
    ),
    rootConnected = rootConnected,
    servicePing = servicePing,
    runtimeState = runtimeState,
    selectedResourceId = selectedResourceId,
    selectedTaskId = selectedTaskId,
    checkedTaskIds = checkedTaskIds,
    taskOptionSelectionsByTask = taskOptionSelectionsByTask,
    taskInputValuesByTask = taskInputValuesByTask,
    sharedOptionSelectionsByScope = sharedOptionSelectionsByScope,
    sharedInputValuesByScope = sharedInputValuesByScope,
    selectedPresetId = selectedPresetId,
    logLevel = settings.logLevel,
    displayLogs = displayLogs,
    lastMessage = lastMessage,
    busy = busy,
)

private fun MainViewModel.hostActions() = MaaHostActions(
    selectTab = { selectTab(it.toAppTab()) },
    selectResource = ::selectResource,
    selectTask = ::selectTask,
    toggleTaskChecked = ::toggleTaskChecked,
    selectPreset = ::selectPreset,
    updateLogLevel = ::updateLogLevel,
    updateTaskSwitchOption = ::updateTaskSwitchOption,
    toggleTaskCheckboxOption = ::toggleTaskCheckboxOption,
    updateTaskInputValue = ::updateTaskInputValue,
    updateSharedSwitchOption = ::updateSharedSwitchOption,
    toggleSharedCheckboxOption = ::toggleSharedCheckboxOption,
    updateSharedInputValue = ::updateSharedInputValue,
    refreshResourceRepository = ::refreshResourceRepository,
    requestClearResourceRepositoryConfirmation = ::requestClearResourceRepositoryConfirmation,
    dismissClearResourceRepositoryConfirmation = ::dismissClearResourceRepositoryConfirmation,
    clearResourceRepository = ::clearResourceRepository,
    requestRootAndConnect = ::requestRootAndConnect,
    prepareRuntime = ::prepareRuntime,
    startWindowedGame = ::startWindowedGame,
    startSelectedTask = ::startSelectedTask,
    startSelectedPreset = ::startSelectedPreset,
    stopRun = ::stopRun,
    toggleDisplayPower = ::toggleDisplayPower,
    exportDiagnostics = ::exportDiagnostics,
    exportConfig = ::exportConfig,
    importConfig = ::importConfig,
    setPreviewSurface = ::setPreviewSurface,
    onPreviewTouchDown = ::onPreviewTouchDown,
    onPreviewTouchMove = ::onPreviewTouchMove,
    onPreviewTouchUp = ::onPreviewTouchUp,
)

private fun MaaEndTab.toHostTab() = when (this) {
    MaaEndTab.HOME -> MaaHostTab.Home
    MaaEndTab.TASKS -> MaaHostTab.Tasks
    MaaEndTab.SETTINGS -> MaaHostTab.Settings
    MaaEndTab.LOGS -> MaaHostTab.Logs
}

private fun MaaHostTab.toAppTab() = when (this) {
    MaaHostTab.Home -> MaaEndTab.HOME
    MaaHostTab.Tasks -> MaaEndTab.TASKS
    MaaHostTab.Settings -> MaaEndTab.SETTINGS
    MaaHostTab.Logs -> MaaEndTab.LOGS
}

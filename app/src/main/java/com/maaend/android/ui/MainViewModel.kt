package com.maaend.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.maaend.android.catalog.InterfaceCatalogLoader
import com.maaend.android.ipc.IRootRuntimeService
import com.maaend.android.model.CatalogSnapshot
import com.maaend.android.model.PresetDescriptor
import com.maaend.android.model.RunRequest
import com.maaend.android.model.RuntimeStateSnapshot
import com.maaend.android.root.RootManager
import com.maaend.android.root.RootRuntimeConnector
import com.maaend.android.storage.AppSettings
import com.maaend.android.storage.AppSettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class MaaEndTab {
    HOME,
    TASKS,
    PRESETS,
    RUNNING,
    SETTINGS,
    LOGS,
}

data class MainUiState(
    val activeTab: MaaEndTab = MaaEndTab.HOME,
    val catalog: CatalogSnapshot = CatalogSnapshot(),
    val settings: AppSettings = AppSettings(),
    val rootAvailable: Boolean = false,
    val rootGranted: Boolean = false,
    val rootConnected: Boolean = false,
    val servicePing: String = "",
    val runtimeState: RuntimeStateSnapshot = RuntimeStateSnapshot(),
    val selectedTaskId: String? = null,
    val selectedPresetId: String? = null,
    val lastMessage: String = "",
    val busy: Boolean = false,
)

class MainViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val catalogLoader = InterfaceCatalogLoader(application.assets)
    private val settingsRepository = AppSettingsRepository(application)
    private val rootConnector = RootRuntimeConnector(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var service: IRootRuntimeService? = null
    private var pollJob: Job? = null

    init {
        val settings = settingsRepository.load()
        val catalog = catalogLoader.load()
        _uiState.value = _uiState.value.copy(
            catalog = catalog,
            settings = settings,
            selectedTaskId = settings.lastSelectedTaskId ?: catalog.tasks.firstOrNull()?.id,
            selectedPresetId = settings.lastSelectedPresetId ?: catalog.presets.firstOrNull()?.id,
            rootAvailable = RootManager.isAvailable(),
            rootGranted = RootManager.isGranted(),
            lastMessage = "Android Root-only MVP scaffold ready",
        )
    }

    fun selectTab(tab: MaaEndTab) {
        _uiState.value = _uiState.value.copy(activeTab = tab)
    }

    fun selectTask(taskId: String) {
        settingsRepository.saveLastTaskId(taskId)
        _uiState.value = _uiState.value.copy(selectedTaskId = taskId)
    }

    fun selectPreset(presetId: String) {
        settingsRepository.saveLastPresetId(presetId)
        _uiState.value = _uiState.value.copy(selectedPresetId = presetId)
    }

    fun updateLogLevel(logLevel: String) {
        settingsRepository.saveLogLevel(logLevel)
        _uiState.value = _uiState.value.copy(
            settings = _uiState.value.settings.copy(logLevel = logLevel),
        )
    }

    fun requestRootAndConnect() {
        viewModelScope.launch {
            setBusy(true, "Requesting Root")
            val granted = RootManager.requestPermission()
            if (!granted) {
                _uiState.value = _uiState.value.copy(
                    busy = false,
                    rootGranted = false,
                    lastMessage = "Root permission denied",
                )
                return@launch
            }

            val result = rootConnector.connect()
            result.onSuccess { runtimeService ->
                service = runtimeService
                _uiState.value = _uiState.value.copy(
                    busy = false,
                    rootAvailable = true,
                    rootGranted = true,
                    rootConnected = true,
                    servicePing = runtimeService.ping(),
                    lastMessage = "Root runtime connected",
                )
                startPolling()
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    busy = false,
                    rootConnected = false,
                    lastMessage = "Failed to connect Root runtime: ${it.message}",
                )
            }
        }
    }

    fun prepareRuntime() {
        viewModelScope.launch {
            val runtimeService = service ?: run {
                _uiState.value = _uiState.value.copy(lastMessage = "Root runtime not connected")
                return@launch
            }

            setBusy(true, "Preparing runtime")
            val prepared = runCatching { runtimeService.prepareRuntime() }.getOrDefault(false)
            refreshRuntimeState()
            _uiState.value = _uiState.value.copy(
                busy = false,
                lastMessage = if (prepared) "Runtime prepared" else "Runtime prepare failed",
            )
        }
    }

    fun startSelectedTask() {
        val taskId = _uiState.value.selectedTaskId ?: return
        val request = RunRequest(
            taskId = taskId,
            resourceName = "官服",
            logLevel = _uiState.value.settings.logLevel,
        )
        startRun(request)
    }

    fun startSelectedPreset() {
        val preset = selectedPreset() ?: return
        val request = RunRequest(
            presetId = preset.id,
            sequenceTaskIds = preset.taskIds,
            resourceName = "官服",
            logLevel = _uiState.value.settings.logLevel,
        )
        startRun(request)
    }

    fun stopRun() {
        viewModelScope.launch {
            runCatching { service?.stopRun() }
            refreshRuntimeState()
        }
    }

    fun exportDiagnostics() {
        viewModelScope.launch {
            val path = runCatching { service?.exportDiagnostics().orEmpty() }.getOrElse { "" }
            refreshRuntimeState()
            if (path.isNotBlank()) {
                _uiState.value = _uiState.value.copy(lastMessage = "Diagnostics exported: $path")
            }
        }
    }

    private fun startRun(request: RunRequest) {
        viewModelScope.launch {
            val runtimeService = service ?: run {
                _uiState.value = _uiState.value.copy(lastMessage = "Root runtime not connected")
                return@launch
            }

            val started = runCatching {
                runtimeService.startRun(json.encodeToString(request))
            }.getOrDefault(false)
            refreshRuntimeState()
            _uiState.value = _uiState.value.copy(
                lastMessage = if (started) "Run started" else "Run failed to start",
                activeTab = MaaEndTab.RUNNING,
            )
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                refreshRuntimeState()
                delay(1_000)
            }
        }
    }

    private suspend fun refreshRuntimeState() {
        val runtimeService = service ?: return
        runCatching {
            val state = json.decodeFromString<RuntimeStateSnapshot>(runtimeService.getState())
            _uiState.value = _uiState.value.copy(
                runtimeState = state,
                servicePing = runtimeService.ping(),
            )
        }
    }

    private fun selectedPreset(): PresetDescriptor? {
        return _uiState.value.catalog.presets.firstOrNull { it.id == _uiState.value.selectedPresetId }
    }

    private fun setBusy(busy: Boolean, message: String) {
        _uiState.value = _uiState.value.copy(busy = busy, lastMessage = message)
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
        rootConnector.disconnect(service)
        service = null
    }
}

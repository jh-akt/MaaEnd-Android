package com.maaend.android.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.maaend.android.catalog.InterfaceCatalogLoader
import com.maaend.android.ipc.IRootRuntimeService
import com.maaend.android.model.CatalogSnapshot
import com.maaend.android.model.ResourceDescriptor
import com.maaend.android.model.RunRequest
import com.maaend.android.model.RuntimeLogChunk
import com.maaend.android.model.RuntimeStateSnapshot
import com.maaend.android.model.TaskDescriptor
import com.maaend.android.model.TaskOptionDescriptor
import com.maaend.android.root.RootManager
import com.maaend.android.root.RootRuntimeConnector
import com.maaend.android.runtime.PersistentResourceRepositoryManager
import com.maaend.android.runtime.PersistentResourceRepositoryStatus
import com.maaend.android.storage.AppSettings
import com.maaend.android.storage.AppSettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.InputStream
import java.io.OutputStream
import java.io.File

enum class MaaEndTab {
    HOME,
    TASKS,
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
    val selectedResourceId: String? = null,
    val selectedTaskId: String? = null,
    val selectedPresetId: String? = null,
    val checkedTaskIds: Set<String> = emptySet(),
    val taskOptionSelectionsByTask: Map<String, Map<String, Set<String>>> = emptyMap(),
    val taskInputValuesByTask: Map<String, Map<String, Map<String, String>>> = emptyMap(),
    val sharedOptionSelectionsByScope: Map<String, Map<String, Set<String>>> = emptyMap(),
    val sharedInputValuesByScope: Map<String, Map<String, Map<String, String>>> = emptyMap(),
    val resourceRepository: PersistentResourceRepositoryStatus = PersistentResourceRepositoryStatus(),
    val resourceRepositoryUpdating: Boolean = false,
    val displayLogs: List<String> = emptyList(),
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
    private var connectJob: Job? = null
    private var previewSurface: Surface? = null
    private var logCursor: Long = 0L
    private val inputPlaceholderRegex = Regex("\\{([A-Za-z0-9_]+)\\}")
    private var screenOnReceiverRegistered = false
    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_SCREEN_ON) {
                return
            }
            if (!_uiState.value.runtimeState.displayPowerOffActive) {
                return
            }
            viewModelScope.launch {
                restoreDisplayPowerAfterScreenOn()
            }
        }
    }

    init {
        val settings = runCatching { settingsRepository.load() }
            .getOrElse { error ->
                Log.e(TAG, "Failed to load app settings", error)
                AppSettings()
            }
        clearLegacyLogCache(application)
        var lastMessage = "Root 运行环境已就绪"
        val resourceRepository = PersistentResourceRepositoryManager.loadStatus(application)
        val catalog = runCatching { loadCatalogSnapshot(resourceRepository) }
            .getOrElse { error ->
                Log.e(TAG, "Failed to load interface catalog", error)
                lastMessage = "接口资源缺失或格式异常：${error.message ?: error::class.java.simpleName}"
                CatalogSnapshot()
            }
        val rootAvailable = runCatching { RootManager.isAvailable() }
            .getOrElse { error ->
                Log.e(TAG, "Failed to detect root availability", error)
                false
            }
        val rootGranted = runCatching { RootManager.isGranted() }
            .getOrElse { error ->
                Log.e(TAG, "Failed to detect root grant state", error)
                false
            }
        val selectedResourceId = settings.selectedResourceId ?: catalog.resources.firstOrNull()?.id
        val visibleTasks = visibleTasks(catalog.tasks, selectedResourceId)
        val selectedTaskId = settings.lastSelectedTaskId
            ?.takeIf { taskId -> visibleTasks.any { it.id == taskId } }
            ?: visibleTasks.firstOrNull()?.id
        _uiState.value = _uiState.value.copy(
            catalog = catalog,
            settings = settings,
            selectedResourceId = selectedResourceId,
            selectedTaskId = selectedTaskId,
            selectedPresetId = settings.lastSelectedPresetId ?: catalog.presets.firstOrNull()?.id,
            checkedTaskIds = settings.checkedTaskIds.ifEmpty {
                setOfNotNull(selectedTaskId)
            }.filterTo(linkedSetOf()) { taskId -> catalog.tasks.any { it.id == taskId } },
            taskOptionSelectionsByTask = mergeStoredOptionSelections(catalog.tasks, settings.taskOptionSelectionsByTask),
            taskInputValuesByTask = mergeStoredInputValues(catalog.tasks, settings.taskInputValuesByTask),
            sharedOptionSelectionsByScope = mergeStoredOptionSelectionsByScope(catalog, settings.sharedOptionSelectionsByScope),
            sharedInputValuesByScope = mergeStoredInputValuesByScope(catalog, settings.sharedInputValuesByScope),
            resourceRepository = resourceRepository,
            rootAvailable = rootAvailable,
            rootGranted = rootGranted,
            lastMessage = lastMessage,
        )
        if (!resourceRepository.available) {
            viewModelScope.launch {
                syncPersistentResourceRepository(force = false, silent = true)
            }
        }
        if (rootAvailable) {
            requestRootAndConnectSilently()
        }
    }

    fun selectTab(tab: MaaEndTab) {
        _uiState.value = _uiState.value.copy(activeTab = tab)
    }

    fun selectTask(taskId: String) {
        settingsRepository.saveLastTaskId(taskId)
        _uiState.value = _uiState.value.copy(selectedTaskId = taskId)
    }

    fun selectResource(resourceId: String) {
        settingsRepository.saveSelectedResourceId(resourceId)
        val visibleTasks = visibleTasks(_uiState.value.catalog.tasks, resourceId)
        val selectedTaskId = _uiState.value.selectedTaskId
            ?.takeIf { taskId -> visibleTasks.any { it.id == taskId } }
            ?: visibleTasks.firstOrNull()?.id
        selectedTaskId?.let(settingsRepository::saveLastTaskId)
        _uiState.value = _uiState.value.copy(
            selectedResourceId = resourceId,
            selectedTaskId = selectedTaskId,
        )
    }

    fun toggleTaskChecked(taskId: String, checked: Boolean) {
        val updated = _uiState.value.checkedTaskIds.toMutableSet().apply {
            if (checked) {
                add(taskId)
            } else {
                remove(taskId)
            }
        }
        settingsRepository.saveCheckedTaskIds(updated)
        _uiState.value = _uiState.value.copy(checkedTaskIds = updated)
    }

    fun updateLogLevel(logLevel: String) {
        settingsRepository.saveLogLevel(logLevel)
        _uiState.value = _uiState.value.copy(
            settings = _uiState.value.settings.copy(logLevel = logLevel),
        )
    }

    fun exportConfig(outputStream: OutputStream) {
        viewModelScope.launch {
            runCatching { settingsRepository.exportTo(outputStream) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(lastMessage = "配置已导出")
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        lastMessage = "导出配置失败：${error.message ?: error::class.java.simpleName}",
                    )
                }
        }
    }

    fun importConfig(inputStream: InputStream) {
        viewModelScope.launch {
            runCatching { settingsRepository.importFrom(inputStream) }
                .onSuccess { importedSettings ->
                    applyLoadedSettings(importedSettings)
                    _uiState.value = _uiState.value.copy(lastMessage = "配置已导入")
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        lastMessage = "导入配置失败：${error.message ?: error::class.java.simpleName}",
                    )
                }
        }
    }

    fun requestRootAndConnect() {
        if (connectJob?.isActive == true) {
            return
        }
        connectJob = viewModelScope.launch {
            connectRootRuntime(silent = false)
        }
    }

    fun prepareRuntime() {
        viewModelScope.launch {
            val runtimeService = requireRuntimeService() ?: run {
                return@launch
            }

            setBusy(true, "准备运行时中")
            val prepared = runCatching { runtimeService.prepareRuntime() }.getOrDefault(false)
            refreshRuntimeState()
            _uiState.value = _uiState.value.copy(
                busy = false,
                lastMessage = if (prepared) "运行时已准备完成" else "运行时准备失败",
            )
        }
    }

    fun refreshResourceRepository() {
        viewModelScope.launch {
            syncPersistentResourceRepository(force = true, silent = false)
        }
    }

    fun startSelectedTask() {
        val tasks = checkedTasksInOrder().ifEmpty {
            selectedTask()?.let(::listOf).orEmpty()
        }
        if (tasks.isEmpty()) {
            return
        }
        val selectedResource = selectedResource()
        val validationError = validateSelections(tasks, selectedResource)
        if (validationError != null) {
            _uiState.value = _uiState.value.copy(lastMessage = validationError)
            return
        }
        val overridesByTask = buildOptionOverridesByTask(tasks, selectedResource)
        val sequenceTaskIds = tasks.map { it.id }
        val request = RunRequest(
            taskId = tasks.first().id,
            sequenceTaskIds = sequenceTaskIds,
            resourceName = selectedResource?.id ?: "官服",
            logLevel = _uiState.value.settings.logLevel,
            optionOverridesJson = overridesByTask[tasks.first().id],
            optionOverridesByTask = overridesByTask,
        )
        startRun(request)
    }

    fun updateTaskSwitchOption(taskId: String, optionId: String, caseName: String) {
        updateTaskOptionSelections(taskId) { current ->
            current + (optionId to setOf(caseName))
        }
    }

    private suspend fun syncPersistentResourceRepository(force: Boolean, silent: Boolean) {
        if (_uiState.value.resourceRepositoryUpdating) {
            return
        }
        _uiState.value = _uiState.value.copy(
            resourceRepositoryUpdating = true,
            lastMessage = if (silent) _uiState.value.lastMessage else "正在同步 GitHub 资源仓库",
        )
        val application = getApplication<Application>()
        val status = runCatching {
            withContext(Dispatchers.IO) {
                if (force) {
                    PersistentResourceRepositoryManager.updateFromGithub(application) { message ->
                        Log.i(TAG, message)
                    }
                } else {
                    PersistentResourceRepositoryManager.ensureAvailable(application) { message ->
                        Log.i(TAG, message)
                    }
                }
            }
        }.getOrElse { error ->
            Log.e(TAG, "Failed to sync GitHub resource repository", error)
            PersistentResourceRepositoryManager.loadStatus(application).copy(
                lastError = error.message ?: error::class.java.simpleName,
            )
        }

        val nextMessage = when {
            status.available && force -> "GitHub 资源已更新，下次准备运行时会使用新资源"
            status.available && !silent -> "GitHub 资源仓库已就绪"
            status.available -> _uiState.value.lastMessage
            else -> "GitHub 资源同步失败，继续使用内置资源：${status.lastError ?: "未知错误"}"
        }
        refreshCatalogSnapshot(status)
        _uiState.value = _uiState.value.copy(
            resourceRepository = status,
            resourceRepositoryUpdating = false,
            lastMessage = nextMessage,
        )
    }

    private fun loadCatalogSnapshot(resourceRepository: PersistentResourceRepositoryStatus): CatalogSnapshot {
        val application = getApplication<Application>()
        return if (resourceRepository.available) {
            catalogLoader.loadFromDirectory(PersistentResourceRepositoryManager.currentRoot(application))
        } else {
            catalogLoader.load()
        }
    }

    private fun refreshCatalogSnapshot(resourceRepository: PersistentResourceRepositoryStatus) {
        val catalog = runCatching { loadCatalogSnapshot(resourceRepository) }
            .getOrElse { error ->
                Log.e(TAG, "Failed to reload catalog from current resource source", error)
                return
            }
        val settings = _uiState.value.settings
        val selectedResourceId = _uiState.value.selectedResourceId
            ?.takeIf { id -> catalog.resources.any { it.id == id } }
            ?: settings.selectedResourceId?.takeIf { id -> catalog.resources.any { it.id == id } }
            ?: catalog.resources.firstOrNull()?.id
        val visibleTasks = visibleTasks(catalog.tasks, selectedResourceId)
        val selectedTaskId = _uiState.value.selectedTaskId
            ?.takeIf { taskId -> visibleTasks.any { it.id == taskId } }
            ?: settings.lastSelectedTaskId?.takeIf { taskId -> visibleTasks.any { it.id == taskId } }
            ?: visibleTasks.firstOrNull()?.id
        _uiState.value = _uiState.value.copy(
            catalog = catalog,
            selectedResourceId = selectedResourceId,
            selectedTaskId = selectedTaskId,
            checkedTaskIds = _uiState.value.checkedTaskIds.filterTo(linkedSetOf()) { taskId ->
                catalog.tasks.any { it.id == taskId }
            },
            taskOptionSelectionsByTask = mergeStoredOptionSelections(catalog.tasks, _uiState.value.taskOptionSelectionsByTask),
            taskInputValuesByTask = mergeStoredInputValues(catalog.tasks, _uiState.value.taskInputValuesByTask),
            sharedOptionSelectionsByScope = mergeStoredOptionSelectionsByScope(catalog, _uiState.value.sharedOptionSelectionsByScope),
            sharedInputValuesByScope = mergeStoredInputValuesByScope(catalog, _uiState.value.sharedInputValuesByScope),
        )
    }

    fun toggleTaskCheckboxOption(taskId: String, optionId: String, caseName: String) {
        updateTaskOptionSelections(taskId) { current ->
            val existing = current[optionId].orEmpty()
            val updated = existing.toMutableSet().apply {
                if (!add(caseName)) {
                    remove(caseName)
                }
            }
            current + (optionId to updated)
        }
    }

    fun updateTaskInputValue(taskId: String, optionId: String, inputName: String, value: String) {
        val currentTaskInputs = _uiState.value.taskInputValuesByTask[taskId].orEmpty()
        val currentOptionInputs = currentTaskInputs[optionId].orEmpty()
        val updatedTaskInputs = currentTaskInputs + (optionId to (currentOptionInputs + (inputName to value)))
        val updatedAllInputs = _uiState.value.taskInputValuesByTask + (taskId to updatedTaskInputs)
        settingsRepository.saveTaskInputValuesByTask(updatedAllInputs)
        _uiState.value = _uiState.value.copy(
            taskInputValuesByTask = updatedAllInputs,
        )
    }

    fun updateSharedSwitchOption(scopeId: String, optionId: String, caseName: String) {
        updateSharedOptionSelections(scopeId) { current ->
            current + (optionId to setOf(caseName))
        }
    }

    fun toggleSharedCheckboxOption(scopeId: String, optionId: String, caseName: String) {
        updateSharedOptionSelections(scopeId) { current ->
            val existing = current[optionId].orEmpty()
            val updated = existing.toMutableSet().apply {
                if (!add(caseName)) {
                    remove(caseName)
                }
            }
            current + (optionId to updated)
        }
    }

    fun updateSharedInputValue(scopeId: String, optionId: String, inputName: String, value: String) {
        val currentScopeInputs = _uiState.value.sharedInputValuesByScope[scopeId].orEmpty()
        val currentOptionInputs = currentScopeInputs[optionId].orEmpty()
        val updatedScopeInputs = currentScopeInputs + (optionId to (currentOptionInputs + (inputName to value)))
        val updatedAllInputs = _uiState.value.sharedInputValuesByScope + (scopeId to updatedScopeInputs)
        settingsRepository.saveSharedInputValuesByScope(updatedAllInputs)
        _uiState.value = _uiState.value.copy(
            sharedInputValuesByScope = updatedAllInputs,
        )
    }

    fun stopRun() {
        viewModelScope.launch {
            runCatching { service?.stopRun() }
            refreshRuntimeState()
        }
    }

    fun toggleDisplayPower() {
        viewModelScope.launch {
            val runtimeService = requireRuntimeService() ?: return@launch
            val turnOn = _uiState.value.runtimeState.displayPowerOffActive
            val changed = runCatching { runtimeService.setDisplayPower(turnOn) }.getOrDefault(false)
            refreshRuntimeState()
            _uiState.value = _uiState.value.copy(
                lastMessage = when {
                    !changed -> "息屏挂机切换失败"
                    turnOn -> "已恢复亮屏"
                    else -> "已进入息屏挂机，按电源键可唤醒"
                },
            )
        }
    }

    fun exportDiagnostics() {
        viewModelScope.launch {
            val runtimeService = requireRuntimeService() ?: return@launch
            val path = runCatching { runtimeService.exportDiagnostics().orEmpty() }.getOrElse { "" }
            refreshRuntimeState()
            if (path.isNotBlank()) {
                _uiState.value = _uiState.value.copy(lastMessage = "诊断包已导出：$path")
            }
        }
    }

    fun setPreviewSurface(surface: Surface?) {
        previewSurface = surface
        val runtimeService = service ?: return
        runCatching { runtimeService.setMonitorSurface(surface) }
    }

    fun startWindowedGame() {
        viewModelScope.launch {
            val runtimeService = requireRuntimeService() ?: return@launch
            val ok = runCatching { runtimeService.startWindowedGame() }.getOrDefault(false)
            refreshRuntimeState()
            _uiState.value = _uiState.value.copy(
                lastMessage = if (ok) "已在应用内拉起窗口模式" else "窗口模式启动失败",
                activeTab = MaaEndTab.TASKS,
            )
        }
    }

    fun onPreviewTouchDown(x: Int, y: Int): Boolean {
        return runCatching { service?.touchDown(x, y) ?: false }.getOrDefault(false)
    }

    fun onPreviewTouchMove(x: Int, y: Int): Boolean {
        return runCatching { service?.touchMove(x, y) ?: false }.getOrDefault(false)
    }

    fun onPreviewTouchUp(x: Int, y: Int): Boolean {
        return runCatching { service?.touchUp(x, y) ?: false }.getOrDefault(false)
    }

    fun getWindowedDisplayId(): Int {
        return runCatching { service?.windowedDisplayId ?: -1 }.getOrDefault(-1)
    }

    private fun startRun(request: RunRequest) {
        viewModelScope.launch {
            val runtimeService = requireRuntimeService() ?: return@launch

            val started = runCatching {
                runtimeService.startRun(json.encodeToString(request))
            }.getOrDefault(false)
            refreshRuntimeState()
            _uiState.value = _uiState.value.copy(
                lastMessage = if (started) "任务已开始执行" else "任务启动失败",
                activeTab = MaaEndTab.TASKS,
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
            val polled = withContext(Dispatchers.IO) {
                val state = json.decodeFromString<RuntimeStateSnapshot>(runtimeService.getState())
                val logChunk = json.decodeFromString<RuntimeLogChunk>(
                    runtimeService.readLogChunk(logCursor, LOG_CHUNK_LINES),
                )
                state to logChunk
            }
            val (state, logChunk) = polled
            val displayLogs = mergeDisplayLogs(logChunk)
            _uiState.value = _uiState.value.copy(
                runtimeState = state,
                servicePing = runtimeService.ping(),
                displayLogs = displayLogs,
            )
            syncScreenOnReceiver(state.displayPowerOffActive)
        }
    }

    private suspend fun restoreDisplayPowerAfterScreenOn() {
        val runtimeService = service ?: return
        val restored = runCatching { runtimeService.setDisplayPower(true) }.getOrDefault(false)
        refreshRuntimeState()
        if (restored) {
            _uiState.value = _uiState.value.copy(lastMessage = "检测到亮屏，已退出息屏挂机")
        }
    }

    private fun requestRootAndConnectSilently() {
        if (connectJob?.isActive == true) {
            return
        }
        connectJob = viewModelScope.launch {
            connectRootRuntime(silent = true)
        }
    }

    private suspend fun requireRuntimeService(): IRootRuntimeService? {
        currentAliveService()?.let { return it }
        connectJob?.takeIf { it.isActive }?.join()
        currentAliveService()?.let { return it }
        return connectRootRuntime(silent = false)
    }

    private suspend fun connectRootRuntime(silent: Boolean): IRootRuntimeService? {
        currentAliveService()?.let { runtimeService ->
            if (!silent) {
                _uiState.value = _uiState.value.copy(lastMessage = "Root Runtime 已连接")
            }
            return runtimeService
        }

        val rootAvailable = runCatching { RootManager.isAvailable() }.getOrDefault(false)
        _uiState.value = _uiState.value.copy(
            busy = true,
            rootAvailable = rootAvailable,
            rootConnected = false,
            servicePing = "",
        )

        if (!rootAvailable) {
            _uiState.value = _uiState.value.copy(
                busy = false,
                rootGranted = false,
                rootConnected = false,
                lastMessage = if (silent) _uiState.value.lastMessage else "未检测到可用 Root",
            )
            return null
        }

        val granted = RootManager.requestPermission()
        if (!granted) {
            _uiState.value = _uiState.value.copy(
                busy = false,
                rootGranted = false,
                rootConnected = false,
                lastMessage = if (silent) _uiState.value.lastMessage else "Root 授权未通过",
            )
            return null
        }

        val result = rootConnector.connect()
        return result.onSuccess { runtimeService ->
            service = runtimeService
            previewSurface?.let { surface ->
                runCatching { runtimeService.setMonitorSurface(surface) }
            }
            _uiState.value = _uiState.value.copy(
                busy = false,
                rootAvailable = true,
                rootGranted = true,
                rootConnected = true,
                servicePing = runtimeService.ping(),
                lastMessage = if (silent) {
                    "已静默获取 Root 并自动连接 Runtime"
                } else {
                    "Root Runtime 已连接"
                },
            )
            startPolling()
        }.onFailure {
            _uiState.value = _uiState.value.copy(
                busy = false,
                rootAvailable = true,
                rootGranted = true,
                rootConnected = false,
                lastMessage = if (silent) {
                    "自动连接 Runtime 失败，可手动重试"
                } else {
                    "连接 Root Runtime 失败：${it.message}"
                },
            )
        }.getOrNull()
    }

    private suspend fun currentAliveService(): IRootRuntimeService? {
        val existingService = service ?: return null
        val ping = runCatching { existingService.ping() }.getOrNull()
        if (!ping.isNullOrBlank()) {
            refreshRuntimeState()
            _uiState.value = _uiState.value.copy(
                busy = false,
                rootAvailable = true,
                rootGranted = true,
                rootConnected = true,
                servicePing = ping,
            )
            return existingService
        }

        rootConnector.disconnect(existingService)
        service = null
        syncScreenOnReceiver(false)
        _uiState.value = _uiState.value.copy(
            rootConnected = false,
            servicePing = "",
        )
        return null
    }

    private fun selectedTask(): TaskDescriptor? {
        return visibleTasks(
            _uiState.value.catalog.tasks,
            _uiState.value.selectedResourceId,
        ).firstOrNull { it.id == _uiState.value.selectedTaskId }
    }

    private fun checkedTasksInOrder(): List<TaskDescriptor> {
        val checked = _uiState.value.checkedTaskIds
        return visibleTasks(
            _uiState.value.catalog.tasks,
            _uiState.value.selectedResourceId,
        ).filter { it.id in checked }
    }

    private fun selectedResource(): ResourceDescriptor? {
        val selectedId = _uiState.value.selectedResourceId
        return _uiState.value.catalog.resources.firstOrNull { it.id == selectedId }
            ?: _uiState.value.catalog.resources.firstOrNull()
    }

    private fun visibleTasks(
        tasks: List<TaskDescriptor>,
        resourceId: String?,
    ): List<TaskDescriptor> {
        return tasks.filter { task ->
            ProjectInterfaceSupport.taskSupportsResource(task, resourceId)
        }
    }

    private fun setBusy(busy: Boolean, message: String) {
        _uiState.value = _uiState.value.copy(busy = busy, lastMessage = message)
    }

    private fun applyLoadedSettings(settings: AppSettings) {
        val catalog = _uiState.value.catalog
        val selectedResourceId = settings.selectedResourceId ?: catalog.resources.firstOrNull()?.id
        val visibleTasks = visibleTasks(catalog.tasks, selectedResourceId)
        val selectedTaskId = settings.lastSelectedTaskId
            ?.takeIf { taskId -> visibleTasks.any { it.id == taskId } }
            ?: visibleTasks.firstOrNull()?.id

        _uiState.value = _uiState.value.copy(
            settings = settings,
            selectedResourceId = selectedResourceId,
            selectedTaskId = selectedTaskId,
            selectedPresetId = settings.lastSelectedPresetId ?: catalog.presets.firstOrNull()?.id,
            checkedTaskIds = settings.checkedTaskIds.ifEmpty {
                setOfNotNull(selectedTaskId)
            }.filterTo(linkedSetOf()) { taskId -> catalog.tasks.any { it.id == taskId } },
            taskOptionSelectionsByTask = mergeStoredOptionSelections(catalog.tasks, settings.taskOptionSelectionsByTask),
            taskInputValuesByTask = mergeStoredInputValues(catalog.tasks, settings.taskInputValuesByTask),
            sharedOptionSelectionsByScope = mergeStoredOptionSelectionsByScope(catalog, settings.sharedOptionSelectionsByScope),
            sharedInputValuesByScope = mergeStoredInputValuesByScope(catalog, settings.sharedInputValuesByScope),
        )
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
        syncScreenOnReceiver(false)
        runCatching { service?.setDisplayPower(true) }
        runCatching { service?.stopWindowedPreview() }
        previewSurface = null
        rootConnector.disconnect(service)
        service = null
    }

    private fun syncScreenOnReceiver(active: Boolean) {
        val application = getApplication<Application>()
        if (active && !screenOnReceiverRegistered) {
            application.registerReceiver(screenOnReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))
            screenOnReceiverRegistered = true
        } else if (!active && screenOnReceiverRegistered) {
            runCatching { application.unregisterReceiver(screenOnReceiver) }
            screenOnReceiverRegistered = false
        }
    }

    private companion object {
        const val TAG = "MainViewModel"
        const val LOG_CHUNK_LINES = 512
        const val MAX_IN_MEMORY_LOG_LINES = 10_000
    }

    private fun mergeDisplayLogs(chunk: RuntimeLogChunk): List<String> {
        val base = if (chunk.reset) emptyList() else _uiState.value.displayLogs
        val merged = if (chunk.lines.isEmpty()) {
            base
        } else {
            (base + chunk.lines).takeLast(MAX_IN_MEMORY_LOG_LINES)
        }
        logCursor = chunk.nextOffsetBytes
        return merged
    }

    private fun clearLegacyLogCache(application: Application) {
        val logCacheFile = File(application.filesDir, "runtime-log-cache.json")
        runCatching {
            if (logCacheFile.exists() && !logCacheFile.delete()) {
                Log.w(TAG, "Failed to delete legacy log cache file: $logCacheFile")
            }
        }.onFailure { error ->
            Log.w(TAG, "Failed to clear legacy log cache", error)
        }
    }

    private fun buildDefaultOptionSelections(tasks: List<TaskDescriptor>): Map<String, Map<String, Set<String>>> {
        return tasks.mapNotNull { task ->
            buildDefaultSelectionsForOptions(task.options).takeIf { it.isNotEmpty() }?.let { task.id to it }
        }.toMap()
    }

    private fun buildDefaultSelectionsForOptions(options: List<TaskOptionDescriptor>): Map<String, Set<String>> {
        val defaults = mutableMapOf<String, Set<String>>()
        collectDefaultSelections(options, defaults)
        return defaults
    }

    private fun collectDefaultSelections(
        options: List<TaskOptionDescriptor>,
        into: MutableMap<String, Set<String>>,
    ) {
        options.forEach { option ->
            val defaults = ProjectInterfaceSupport.defaultSelectionForOption(option)
            if (defaults.isNotEmpty()) {
                into[option.id] = defaults
            }
            option.cases
                .filter { it.name in defaults }
                .forEach { case ->
                    collectDefaultSelections(case.nestedOptions, into)
                }
        }
    }

    private fun buildDefaultInputValues(tasks: List<TaskDescriptor>): Map<String, Map<String, Map<String, String>>> {
        return tasks.mapNotNull { task ->
            buildDefaultInputValuesForOptions(task.options).takeIf { it.isNotEmpty() }?.let { task.id to it }
        }.toMap()
    }

    private fun buildDefaultInputValuesForOptions(options: List<TaskOptionDescriptor>): Map<String, Map<String, String>> {
        val defaults = mutableMapOf<String, Map<String, String>>()
        collectDefaultInputs(options, defaults)
        return defaults
    }

    private fun mergeStoredOptionSelections(
        tasks: List<TaskDescriptor>,
        stored: Map<String, Map<String, Set<String>>>,
    ): Map<String, Map<String, Set<String>>> {
        val defaults = buildDefaultOptionSelections(tasks).toMutableMap()
        tasks.forEach { task ->
            stored[task.id]?.takeIf { it.isNotEmpty() }?.let { storedTaskSelections ->
                defaults[task.id] = defaults[task.id].orEmpty() + storedTaskSelections
            }
        }
        return defaults
    }

    private fun mergeStoredOptionSelectionsByScope(
        catalog: CatalogSnapshot,
        stored: Map<String, Map<String, Set<String>>>,
    ): Map<String, Map<String, Set<String>>> {
        val scopes = buildSharedOptionScopes(catalog)
        val merged = linkedMapOf<String, Map<String, Set<String>>>()
        scopes.forEach { (scopeId, options) ->
            val defaults = buildDefaultSelectionsForOptions(options)
            val storedSelections = stored[scopeId].orEmpty()
            val combined = defaults + storedSelections
            if (combined.isNotEmpty()) {
                merged[scopeId] = combined
            }
        }
        return merged
    }

    private fun mergeStoredInputValues(
        tasks: List<TaskDescriptor>,
        stored: Map<String, Map<String, Map<String, String>>>,
    ): Map<String, Map<String, Map<String, String>>> {
        val defaults = buildDefaultInputValues(tasks).toMutableMap()
        tasks.forEach { task ->
            val storedTaskInputs = stored[task.id].orEmpty()
            if (storedTaskInputs.isNotEmpty()) {
                val mergedInputs = defaults[task.id].orEmpty().toMutableMap()
                storedTaskInputs.forEach { (optionId, inputValues) ->
                    mergedInputs[optionId] = mergedInputs[optionId].orEmpty() + inputValues
                }
                defaults[task.id] = mergedInputs
            }
        }
        return defaults
    }

    private fun mergeStoredInputValuesByScope(
        catalog: CatalogSnapshot,
        stored: Map<String, Map<String, Map<String, String>>>,
    ): Map<String, Map<String, Map<String, String>>> {
        val scopes = buildSharedOptionScopes(catalog)
        val merged = linkedMapOf<String, Map<String, Map<String, String>>>()
        scopes.forEach { (scopeId, options) ->
            val defaults = buildDefaultInputValuesForOptions(options).toMutableMap()
            val storedScopeInputs = stored[scopeId].orEmpty()
            if (storedScopeInputs.isNotEmpty()) {
                storedScopeInputs.forEach { (optionId, inputValues) ->
                    defaults[optionId] = defaults[optionId].orEmpty() + inputValues
                }
            }
            if (defaults.isNotEmpty()) {
                merged[scopeId] = defaults
            }
        }
        return merged
    }

    private fun collectDefaultInputs(
        options: List<TaskOptionDescriptor>,
        into: MutableMap<String, Map<String, String>>,
    ) {
        options.forEach { option ->
            if (option.inputs.isNotEmpty()) {
                into[option.id] = option.inputs.associate { it.name to it.defaultValue }
            }
            val defaults = ProjectInterfaceSupport.defaultSelectionForOption(option)
            option.cases
                .filter { it.name in defaults }
                .forEach { case ->
                    collectDefaultInputs(case.nestedOptions, into)
                }
        }
    }

    private fun updateTaskOptionSelections(
        taskId: String,
        transform: (Map<String, Set<String>>) -> Map<String, Set<String>>,
    ) {
        val task = _uiState.value.catalog.tasks.firstOrNull { it.id == taskId }
        val current = _uiState.value.taskOptionSelectionsByTask[taskId]
            ?: task?.let { buildDefaultSelectionsForOptions(it.options) }
            ?: emptyMap()
        val updated = transform(current).filterValues { it.isNotEmpty() }
        val updatedAllSelections = _uiState.value.taskOptionSelectionsByTask + (taskId to updated)
        settingsRepository.saveTaskOptionSelectionsByTask(updatedAllSelections)
        _uiState.value = _uiState.value.copy(taskOptionSelectionsByTask = updatedAllSelections)
    }

    private fun updateSharedOptionSelections(
        scopeId: String,
        transform: (Map<String, Set<String>>) -> Map<String, Set<String>>,
    ) {
        val options = buildSharedOptionScopes(_uiState.value.catalog)[scopeId].orEmpty()
        val current = _uiState.value.sharedOptionSelectionsByScope[scopeId]
            ?: buildDefaultSelectionsForOptions(options)
        val updated = transform(current).filterValues { it.isNotEmpty() }
        val updatedAllSelections = _uiState.value.sharedOptionSelectionsByScope + (scopeId to updated)
        settingsRepository.saveSharedOptionSelectionsByScope(updatedAllSelections)
        _uiState.value = _uiState.value.copy(sharedOptionSelectionsByScope = updatedAllSelections)
    }

    private fun buildOptionOverridesByTask(
        tasks: List<TaskDescriptor>,
        resource: ResourceDescriptor?,
    ): Map<String, String> {
        val sharedOverride = buildSharedOptionOverride(resource)
        return tasks.mapNotNull { task ->
            mergeOverrideJson(
                sharedOverride,
                buildTaskOptionOverride(task, resource?.id),
            )?.let { task.id to it }
        }.toMap()
    }

    private fun buildSharedOptionOverride(resource: ResourceDescriptor?): String? {
        val resourceId = resource?.id
        val globalOverride = buildOptionOverride(
            options = ProjectInterfaceSupport.filterOptionsForResource(
                _uiState.value.catalog.globalOptions,
                resourceId,
            ),
            selectedByOption = _uiState.value.sharedOptionSelectionsByScope[ProjectInterfaceSupport.GLOBAL_SCOPE_ID].orEmpty(),
            inputValuesByOption = _uiState.value.sharedInputValuesByScope[ProjectInterfaceSupport.GLOBAL_SCOPE_ID].orEmpty(),
        )
        val resourceScopeId = resource?.id?.let(ProjectInterfaceSupport::resourceScopeId)
        val resourceOverride = buildOptionOverride(
            options = resource?.let {
                ProjectInterfaceSupport.filterOptionsForResource(it.options, resourceId)
            }.orEmpty(),
            selectedByOption = resourceScopeId?.let { _uiState.value.sharedOptionSelectionsByScope[it].orEmpty() }.orEmpty(),
            inputValuesByOption = resourceScopeId?.let { _uiState.value.sharedInputValuesByScope[it].orEmpty() }.orEmpty(),
        )
        return mergeOverrideJson(globalOverride, resourceOverride)
    }

    private fun buildTaskOptionOverride(
        task: TaskDescriptor,
        resourceId: String?,
    ): String? {
        return buildOptionOverride(
            options = ProjectInterfaceSupport.filterOptionsForResource(task.options, resourceId),
            selectedByOption = _uiState.value.taskOptionSelectionsByTask[task.id].orEmpty(),
            inputValuesByOption = _uiState.value.taskInputValuesByTask[task.id].orEmpty(),
        )
    }

    private fun buildOptionOverride(
        options: List<TaskOptionDescriptor>,
        selectedByOption: Map<String, Set<String>>,
        inputValuesByOption: Map<String, Map<String, String>>,
    ): String? {
        if (options.isEmpty()) {
            return null
        }

        var merged = JsonObject(emptyMap())
        var hasOverride = false
        applyOptionOverrides(
            options = options,
            selectedByOption = selectedByOption,
            inputValuesByOption = inputValuesByOption,
            onMerge = { overrideJson ->
                val overrideObject = runCatching {
                    json.parseToJsonElement(overrideJson).jsonObject
                }.getOrNull() ?: return@applyOptionOverrides
                merged = mergeJsonObjects(merged, overrideObject)
                hasOverride = true
            },
        )
        return if (hasOverride) merged.toString() else null
    }

    private fun applyOptionOverrides(
        options: List<TaskOptionDescriptor>,
        selectedByOption: Map<String, Set<String>>,
        inputValuesByOption: Map<String, Map<String, String>>,
        onMerge: (String) -> Unit,
    ) {
        options.forEach { option ->
            when (option.type) {
                com.maaend.android.model.TaskOptionType.Switch,
                com.maaend.android.model.TaskOptionType.Select,
                com.maaend.android.model.TaskOptionType.Checkbox -> {
                    val selectedCaseNames = selectedByOption[option.id].takeUnless { it.isNullOrEmpty() }
                        ?: ProjectInterfaceSupport.defaultSelectionForOption(option)
                    option.cases
                        .filter { it.name in selectedCaseNames }
                        .forEach { optionCase ->
                            onMerge(optionCase.pipelineOverrideJson)
                            applyOptionOverrides(
                                options = optionCase.nestedOptions,
                                selectedByOption = selectedByOption,
                                inputValuesByOption = inputValuesByOption,
                                onMerge = onMerge,
                            )
                        }
                }

                com.maaend.android.model.TaskOptionType.Input -> {
                    val values = buildInputValues(option, inputValuesByOption[option.id].orEmpty())
                    onMerge(applyInputPlaceholders(option.pipelineOverrideJson, values))
                }
            }
        }
    }

    private fun validateSelections(
        tasks: List<TaskDescriptor>,
        resource: ResourceDescriptor?,
    ): String? {
        val resourceId = resource?.id
        val globalErrors = ProjectInterfaceSupport.collectInputValidationErrors(
            options = ProjectInterfaceSupport.filterOptionsForResource(_uiState.value.catalog.globalOptions, resourceId),
            selectedByOption = _uiState.value.sharedOptionSelectionsByScope[ProjectInterfaceSupport.GLOBAL_SCOPE_ID].orEmpty(),
            inputValuesByOption = _uiState.value.sharedInputValuesByScope[ProjectInterfaceSupport.GLOBAL_SCOPE_ID].orEmpty(),
        )
        if (globalErrors.isNotEmpty()) {
            return "全局配置里有未通过校验的输入项"
        }
        val resourceScopeId = resource?.id?.let(ProjectInterfaceSupport::resourceScopeId)
        val resourceErrors = ProjectInterfaceSupport.collectInputValidationErrors(
            options = resource?.let {
                ProjectInterfaceSupport.filterOptionsForResource(it.options, resourceId)
            }.orEmpty(),
            selectedByOption = resourceScopeId?.let { _uiState.value.sharedOptionSelectionsByScope[it].orEmpty() }.orEmpty(),
            inputValuesByOption = resourceScopeId?.let { _uiState.value.sharedInputValuesByScope[it].orEmpty() }.orEmpty(),
        )
        if (resourceErrors.isNotEmpty()) {
            return "资源包配置里有未通过校验的输入项"
        }
        tasks.forEach { task ->
            val taskErrors = ProjectInterfaceSupport.collectInputValidationErrors(
                options = ProjectInterfaceSupport.filterOptionsForResource(task.options, resourceId),
                selectedByOption = _uiState.value.taskOptionSelectionsByTask[task.id].orEmpty(),
                inputValuesByOption = _uiState.value.taskInputValuesByTask[task.id].orEmpty(),
            )
            if (taskErrors.isNotEmpty()) {
                return "任务「${task.label}」里有未通过校验的输入项"
            }
        }
        return null
    }

    private fun buildSharedOptionScopes(catalog: CatalogSnapshot): Map<String, List<TaskOptionDescriptor>> {
        val scopes = linkedMapOf<String, List<TaskOptionDescriptor>>()
        if (catalog.globalOptions.isNotEmpty()) {
            scopes[ProjectInterfaceSupport.GLOBAL_SCOPE_ID] = catalog.globalOptions
        }
        catalog.resources.forEach { resource ->
            if (resource.options.isNotEmpty()) {
                scopes[ProjectInterfaceSupport.resourceScopeId(resource.id)] = resource.options
            }
        }
        return scopes
    }

    private fun buildInputValues(
        option: TaskOptionDescriptor,
        currentValues: Map<String, String>,
    ): Map<String, PipelineInputValue> {
        return option.inputs.associate { input ->
            input.name to PipelineInputValue(
                value = currentValues[input.name] ?: input.defaultValue,
                pipelineType = input.pipelineType,
            )
        }
    }

    private fun applyInputPlaceholders(
        rawJson: String,
        values: Map<String, PipelineInputValue>,
    ): String {
        if (values.isEmpty() || rawJson.isBlank()) {
            return rawJson
        }

        val element = runCatching { json.parseToJsonElement(rawJson) }.getOrNull()
            ?: return replacePlaceholdersInText(rawJson, values)
        return replacePlaceholders(element, values).toString()
    }

    private fun mergeJsonObjects(base: JsonObject, overlay: JsonObject): JsonObject {
        return buildJsonObject {
            val keys = base.keys + overlay.keys
            keys.forEach { key ->
                val baseValue = base[key]
                val overlayValue = overlay[key]
                when {
                    baseValue is JsonObject && overlayValue is JsonObject -> put(key, mergeJsonObjects(baseValue, overlayValue))
                    overlayValue != null -> put(key, overlayValue)
                    baseValue != null -> put(key, baseValue)
                }
            }
        }
    }

    private fun mergeOverrideJson(baseJson: String?, overlayJson: String?): String? {
        val base = parseOverrideObject(baseJson)
        val overlay = parseOverrideObject(overlayJson)
        return when {
            base == null && overlay == null -> null
            base == null -> overlay?.toString()
            overlay == null -> base.toString()
            else -> mergeJsonObjects(base, overlay).toString()
        }
    }

    private fun parseOverrideObject(rawJson: String?): JsonObject? {
        if (rawJson.isNullOrBlank()) {
            return null
        }
        return runCatching { json.parseToJsonElement(rawJson).jsonObject }.getOrNull()
    }

    private fun replacePlaceholders(
        element: JsonElement,
        values: Map<String, PipelineInputValue>,
    ): JsonElement {
        return when (element) {
            is JsonObject -> buildJsonObject {
                element.forEach { (key, value) ->
                    put(key, replacePlaceholders(value, values))
                }
            }

            is JsonArray -> buildJsonArray {
                element.forEach { item ->
                    add(replacePlaceholders(item, values))
                }
            }

            is JsonPrimitive -> {
                if (!element.isString) {
                    element
                } else {
                    replaceStringPlaceholder(element.content, values)
                }
            }

            else -> element
        }
    }

    private fun replaceStringPlaceholder(
        rawValue: String,
        values: Map<String, PipelineInputValue>,
    ): JsonElement {
        val exactMatch = inputPlaceholderRegex.matchEntire(rawValue)
        if (exactMatch != null) {
            val key = exactMatch.groupValues[1]
            values[key]?.let(::toJsonPrimitive)?.let { return it }
        }
        return JsonPrimitive(replacePlaceholdersInText(rawValue, values))
    }

    private fun replacePlaceholdersInText(
        rawValue: String,
        values: Map<String, PipelineInputValue>,
    ): String {
        return inputPlaceholderRegex.replace(rawValue) { match ->
            values[match.groupValues[1]]?.value ?: match.value
        }
    }

    private fun toJsonPrimitive(value: PipelineInputValue): JsonPrimitive {
        return when (value.pipelineType.lowercase()) {
            "int" -> value.value.toLongOrNull()?.let(::JsonPrimitive)
                ?: value.value.toDoubleOrNull()?.let(::JsonPrimitive)
                ?: JsonPrimitive(value.value)

            "bool" -> when (value.value.lowercase()) {
                "true" -> JsonPrimitive(true)
                "false" -> JsonPrimitive(false)
                else -> JsonPrimitive(value.value)
            }

            else -> JsonPrimitive(value.value)
        }
    }

    private data class PipelineInputValue(
        val value: String,
        val pipelineType: String,
    )
}

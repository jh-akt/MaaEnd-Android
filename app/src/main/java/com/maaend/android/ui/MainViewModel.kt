package com.maaend.android.ui

import android.app.Application
import android.util.Log
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.maaend.android.catalog.InterfaceCatalogLoader
import com.maaend.android.ipc.IRootRuntimeService
import com.maaend.android.model.CatalogSnapshot
import com.maaend.android.model.RunRequest
import com.maaend.android.model.RuntimeStateSnapshot
import com.maaend.android.model.TaskDescriptor
import com.maaend.android.model.TaskOptionDescriptor
import com.maaend.android.model.TaskOptionInput
import com.maaend.android.model.TaskOptionType
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

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
    val selectedTaskId: String? = null,
    val selectedPresetId: String? = null,
    val checkedTaskIds: Set<String> = emptySet(),
    val taskOptionSelectionsByTask: Map<String, Map<String, Set<String>>> = emptyMap(),
    val taskInputValuesByTask: Map<String, Map<String, Map<String, String>>> = emptyMap(),
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
    private val inputPlaceholderRegex = Regex("\\{([A-Za-z0-9_]+)\\}")

    init {
        val settings = runCatching { settingsRepository.load() }
            .getOrElse { error ->
                Log.e(TAG, "Failed to load app settings", error)
                AppSettings()
            }
        var lastMessage = "Root 运行环境已就绪"
        val catalog = runCatching { catalogLoader.load() }
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
        _uiState.value = _uiState.value.copy(
            catalog = catalog,
            settings = settings,
            selectedTaskId = settings.lastSelectedTaskId ?: catalog.tasks.firstOrNull()?.id,
            selectedPresetId = settings.lastSelectedPresetId ?: catalog.presets.firstOrNull()?.id,
            checkedTaskIds = settings.checkedTaskIds.ifEmpty {
                setOfNotNull(settings.lastSelectedTaskId ?: catalog.tasks.firstOrNull()?.id)
            }.filterTo(linkedSetOf()) { taskId -> catalog.tasks.any { it.id == taskId } },
            taskOptionSelectionsByTask = mergeStoredOptionSelections(catalog.tasks, settings.taskOptionSelectionsByTask),
            taskInputValuesByTask = mergeStoredInputValues(catalog.tasks, settings.taskInputValuesByTask),
            rootAvailable = rootAvailable,
            rootGranted = rootGranted,
            lastMessage = lastMessage,
        )
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

    fun startSelectedTask() {
        val tasks = checkedTasksInOrder().ifEmpty {
            selectedTask()?.let(::listOf).orEmpty()
        }
        if (tasks.isEmpty()) {
            return
        }
        val overridesByTask = buildOptionOverridesByTask(tasks)
        val sequenceTaskIds = tasks.map { it.id }
        val request = RunRequest(
            taskId = tasks.first().id,
            sequenceTaskIds = sequenceTaskIds,
            resourceName = "官服",
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

    fun stopRun() {
        viewModelScope.launch {
            runCatching { service?.stopRun() }
            refreshRuntimeState()
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
            val state = json.decodeFromString<RuntimeStateSnapshot>(runtimeService.getState())
            _uiState.value = _uiState.value.copy(
                runtimeState = state,
                servicePing = runtimeService.ping(),
            )
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
        _uiState.value = _uiState.value.copy(
            rootConnected = false,
            servicePing = "",
        )
        return null
    }

    private fun selectedTask(): TaskDescriptor? {
        return _uiState.value.catalog.tasks.firstOrNull { it.id == _uiState.value.selectedTaskId }
    }

    private fun checkedTasksInOrder(): List<TaskDescriptor> {
        val checked = _uiState.value.checkedTaskIds
        return _uiState.value.catalog.tasks.filter { it.id in checked }
    }

    private fun setBusy(busy: Boolean, message: String) {
        _uiState.value = _uiState.value.copy(busy = busy, lastMessage = message)
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
        runCatching { service?.stopWindowedPreview() }
        previewSurface = null
        rootConnector.disconnect(service)
        service = null
    }

    private companion object {
        const val TAG = "MainViewModel"
    }

    private fun buildDefaultOptionSelections(tasks: List<TaskDescriptor>): Map<String, Map<String, Set<String>>> {
        return tasks.mapNotNull { task ->
            val defaults = mutableMapOf<String, Set<String>>()
            collectDefaultSelections(task.options, defaults)
            defaults.takeIf { it.isNotEmpty() }?.let { task.id to it }
        }.toMap()
    }

    private fun collectDefaultSelections(
        options: List<TaskOptionDescriptor>,
        into: MutableMap<String, Set<String>>,
    ) {
        options.forEach { option ->
            val defaults = defaultSelectionForOption(option)
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
            val defaults = mutableMapOf<String, Map<String, String>>()
            collectDefaultInputs(task.options, defaults)
            defaults.takeIf { it.isNotEmpty() }?.let { task.id to it }
        }.toMap()
    }

    private fun mergeStoredOptionSelections(
        tasks: List<TaskDescriptor>,
        stored: Map<String, Map<String, Set<String>>>,
    ): Map<String, Map<String, Set<String>>> {
        val defaults = buildDefaultOptionSelections(tasks).toMutableMap()
        tasks.forEach { task ->
            val storedTaskSelections = stored[task.id].orEmpty()
            if (storedTaskSelections.isNotEmpty()) {
                defaults[task.id] = defaults[task.id].orEmpty() + storedTaskSelections
            }
        }
        return defaults
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

    private fun collectDefaultInputs(
        options: List<TaskOptionDescriptor>,
        into: MutableMap<String, Map<String, String>>,
    ) {
        options.forEach { option ->
            if (option.inputs.isNotEmpty()) {
                into[option.id] = option.inputs.associate { it.name to it.defaultValue }
            }
            val defaults = defaultSelectionForOption(option)
            option.cases
                .filter { it.name in defaults }
                .forEach { case ->
                    collectDefaultInputs(case.nestedOptions, into)
                }
        }
    }

    private fun defaultSelectionForOption(option: TaskOptionDescriptor): Set<String> {
        val defaults = option.defaultCaseNames.toSet()
        if (defaults.isNotEmpty()) {
            return defaults
        }
        return when (option.type) {
            TaskOptionType.Switch,
            TaskOptionType.Select -> option.cases.firstOrNull()?.name?.let(::setOf).orEmpty()
            TaskOptionType.Checkbox,
            TaskOptionType.Input -> emptySet()
        }
    }

    private fun updateTaskOptionSelections(
        taskId: String,
        transform: (Map<String, Set<String>>) -> Map<String, Set<String>>,
    ) {
        val current = _uiState.value.taskOptionSelectionsByTask[taskId]
            ?: buildDefaultOptionSelections(_uiState.value.catalog.tasks.filter { it.id == taskId })[taskId]
            ?: emptyMap()
        val updated = transform(current).filterValues { it.isNotEmpty() }
        val updatedAllSelections = _uiState.value.taskOptionSelectionsByTask + (taskId to updated)
        settingsRepository.saveTaskOptionSelectionsByTask(updatedAllSelections)
        _uiState.value = _uiState.value.copy(
            taskOptionSelectionsByTask = updatedAllSelections,
        )
    }

    private fun buildOptionOverridesByTask(tasks: List<TaskDescriptor>): Map<String, String> {
        return tasks.mapNotNull { task ->
            buildTaskOptionOverride(task)?.let { task.id to it }
        }.toMap()
    }

    private fun buildTaskOptionOverride(task: TaskDescriptor): String? {
        if (task.options.isEmpty()) {
            return null
        }

        var merged = JsonObject(emptyMap())
        var hasOverride = false
        val selectedByOption = _uiState.value.taskOptionSelectionsByTask[task.id].orEmpty()
        val inputValuesByOption = _uiState.value.taskInputValuesByTask[task.id].orEmpty()

        applyOptionOverrides(
            options = task.options,
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
                TaskOptionType.Switch,
                TaskOptionType.Select,
                TaskOptionType.Checkbox -> {
                    val selectedCaseNames = selectedByOption[option.id].takeUnless { it.isNullOrEmpty() }
                        ?: defaultSelectionForOption(option)
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

                TaskOptionType.Input -> {
                    val values = buildInputValues(option, inputValuesByOption[option.id].orEmpty())
                    onMerge(applyInputPlaceholders(option.pipelineOverrideJson, values))
                }
            }
        }
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

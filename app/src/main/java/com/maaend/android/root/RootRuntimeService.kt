package com.maaend.android.root

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.view.Surface
import com.maaend.android.catalog.InterfaceCatalogLoader
import com.maaend.android.bridge.DriverClass
import com.maaend.android.bridge.InputControlUtils
import com.maaend.android.bridge.NativeBridgeLib
import com.maaend.android.ipc.IRootRuntimeService
import com.maaend.android.maa.MaaFrameworkBridge
import com.maaend.android.model.FailureArtifact
import com.maaend.android.model.RunRequest
import com.maaend.android.model.RunSessionPhase
import com.maaend.android.model.RuntimeStateSnapshot
import com.maaend.android.preview.ActivityUtils
import com.maaend.android.preview.DefaultDisplayConfig
import com.maaend.android.preview.VirtualDisplayManager
import com.maaend.android.runtime.RuntimeBootstrapper
import com.maaend.android.runtime.RuntimeLogger
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.system.exitProcess

class RootRuntimeService(
    private val context: Context,
) : IRootRuntimeService.Stub() {
    private val catalogLoader = InterfaceCatalogLoader(context.assets)
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val stateLock = Any()
    private val executor = Executors.newSingleThreadExecutor()

    @Volatile
    private var runtimeRoot: File? = null

    @Volatile
    private var logger: RuntimeLogger? = null

    @Volatile
    private var currentFuture: Future<*>? = null

    @Volatile
    private var stopRequested = false

    @Volatile
    private var maaBridge: MaaFrameworkBridge? = null

    @Volatile
    private var preparedGameDisplayId: Int = DefaultDisplayConfig.DISPLAY_NONE

    @Volatile
    private var snapshot = RuntimeStateSnapshot(
        lastMessage = "Root runtime bootstrapped",
    )

    init {
        runCatching {
            InputControlUtils.initialize(context)
            DriverClass.installContext(context)
        }.onFailure { error ->
            Log.e(TAG, "Failed to initialize input controller", error)
        }
    }

    override fun ping(): String {
        return buildString {
            append("MaaEnd Android Root Runtime")
            append(" | prepared=")
            append(snapshot.runtimePrepared)
            append(" | root=")
            append(runtimeRootDirectory().absolutePath)
        }
    }

    override fun prepareRuntime(): Boolean {
        updateSnapshot { it.copy(phase = RunSessionPhase.Preparing, lastMessage = "Preparing runtime") }
        return runCatching {
            val root = runtimeRootDirectory()
            val runtimeLogger = RuntimeLogger(root)
            logger = runtimeLogger
            val result = RuntimeBootstrapper.prepare(context, runtimeLogger, root)
            runtimeRoot = result.runtimeRoot
            preparedGameDisplayId = DefaultDisplayConfig.DISPLAY_NONE

            updateSnapshot {
                it.copy(
                    phase = RunSessionPhase.Idle,
                    runtimePrepared = true,
                    runtimeRoot = result.runtimeRoot.absolutePath,
                    capabilities = result.capabilities,
                    lastMessage = result.message,
                    recentLogs = runtimeLogger.tail(),
                )
            }
            true
        }.getOrElse {
            failRun(taskId = null, message = "Runtime prepare failed: ${it.message}")
            false
        }
    }

    override fun startRun(runRequestJson: String): Boolean {
        val request = runCatching { json.decodeFromString<RunRequest>(runRequestJson) }
            .getOrElse {
                failRun(taskId = null, message = "Invalid run request: ${it.message}")
                return false
            }

        if (snapshot.phase == RunSessionPhase.Running || snapshot.phase == RunSessionPhase.Preparing) {
            return false
        }

        if (!snapshot.runtimePrepared) {
            log("Runtime not prepared, preparing automatically before run")
            if (!prepareRuntime()) {
                failRun(taskId = request.taskId, message = "Runtime prepare failed before run")
                return false
            }
        }

        stopRequested = false
        currentFuture = executor.submit {
            val tasks = request.sequenceTaskIds.ifEmpty {
                request.taskId?.let(::listOf) ?: emptyList()
            }
            val optionOverridesByTask = request.optionOverridesByTask

            if (tasks.isEmpty()) {
                failRun(taskId = null, message = "No task to run")
                return@submit
            }

            log("Run started: ${request.taskId ?: request.presetId}")
            for (taskId in tasks) {
                if (stopRequested) {
                    completeRun(taskId, "Run stopped by user", RunSessionPhase.Completed)
                    return@submit
                }
                if (!runSingleTask(taskId, optionOverridesByTask[taskId])) {
                    return@submit
                }
            }
            completeRun(tasks.last(), "Run completed", RunSessionPhase.Completed)
        }
        return true
    }

    override fun stopRun() {
        stopRequested = true
        maaBridge?.stop()
        preparedGameDisplayId = DefaultDisplayConfig.DISPLAY_NONE
        updateSnapshot { it.copy(phase = RunSessionPhase.Stopping, lastMessage = "Stop requested") }
        currentFuture?.cancel(true)
        updateSnapshot { it.copy(phase = RunSessionPhase.Idle, currentTaskId = null, lastMessage = "Run stopped") }
    }

    override fun setMonitorSurface(surface: Surface?) {
        VirtualDisplayManager.setMonitorSurface(surface)
    }

    override fun startWindowedGame(): Boolean {
        val displayId = VirtualDisplayManager.start(context)
        if (displayId == DefaultDisplayConfig.DISPLAY_NONE) {
            return false
        }
        return ActivityUtils.startApp(
            context = context,
            packageName = "com.hypergryph.endfield",
            displayId = displayId,
            forceStop = true,
            excludeFromRecents = false,
        )
    }

    override fun touchDown(x: Int, y: Int): Boolean {
        return dispatchWindowTouch(x, y) { tx, ty, displayId ->
            DriverClass.touchDown(tx, ty, displayId)
        }
    }

    override fun touchMove(x: Int, y: Int): Boolean {
        return dispatchWindowTouch(x, y) { tx, ty, displayId ->
            DriverClass.touchMove(tx, ty, displayId)
        }
    }

    override fun touchUp(x: Int, y: Int): Boolean {
        return dispatchWindowTouch(x, y) { tx, ty, displayId ->
            DriverClass.touchUp(tx, ty, displayId)
        }
    }

    override fun getWindowedDisplayId(): Int = VirtualDisplayManager.getDisplayId()

    override fun stopWindowedPreview() {
        preparedGameDisplayId = DefaultDisplayConfig.DISPLAY_NONE
        VirtualDisplayManager.stop()
    }

    override fun getState(): String {
        val recentLogs = logger?.tail() ?: emptyList()
        return json.encodeToString(snapshot.copy(recentLogs = recentLogs))
    }

    override fun exportDiagnostics(): String {
        val root = runtimeRoot ?: runtimeRootDirectory()
        val diagnosticsDir = File(root, "diagnostics").apply { mkdirs() }
        val output = File(diagnosticsDir, "MaaEnd-android-diagnostics-${System.currentTimeMillis()}.zip")
        val logFile = logger?.file()
        val stateJson = json.encodeToString(snapshot.copy(recentLogs = logger?.tail() ?: emptyList()))
        val screenshot = snapshot.lastFailure?.screenshotPath?.let(::File)

        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("state.json"))
            zip.write(stateJson.toByteArray())
            zip.closeEntry()

            if (logFile != null && logFile.exists()) {
                zip.putNextEntry(ZipEntry("logs/root-runtime.log"))
                logFile.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }

            if (screenshot != null && screenshot.exists()) {
                zip.putNextEntry(ZipEntry("artifacts/${screenshot.name}"))
                screenshot.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }

        updateSnapshot {
            it.copy(
                lastDiagnosticsPath = output.absolutePath,
                recentLogs = logger?.tail() ?: emptyList(),
            )
        }
        return output.absolutePath
    }

    override fun destroy() {
        stopRequested = true
        VirtualDisplayManager.stop()
        maaBridge?.destroy()
        maaBridge = null
        currentFuture?.cancel(true)
        executor.shutdownNow()
        exitProcess(0)
    }

    private fun runSingleTask(taskId: String, optionOverrideJson: String?): Boolean {
        updateSnapshot {
            it.copy(
                phase = RunSessionPhase.Running,
                currentTaskId = taskId,
                lastMessage = "Running $taskId",
            )
        }
        log("Running task: $taskId")

        return when (taskId) {
            "AndroidOpenGame" -> {
                val capabilities = snapshot.capabilities
                if (capabilities.hasBundledGoService && capabilities.hasBundledMaaFramework) {
                    runAndroidOpenGameTask(optionOverrideJson)
                } else {
                    val displayId = VirtualDisplayManager.getDisplayId()
                    val success = DriverClass.startApp(
                        packageName = "com.hypergryph.endfield",
                        displayId = if (displayId == DefaultDisplayConfig.DISPLAY_NONE) 0 else displayId,
                        forceStop = true,
                    )
                    if (!success) {
                        failRun(taskId, "Failed to launch com.hypergryph.endfield")
                        false
                    } else {
                        log("AndroidOpenGame launch succeeded on display=${if (displayId == DefaultDisplayConfig.DISPLAY_NONE) 0 else displayId}")
                        true
                    }
                }
            }

            else -> {
                val capabilities = snapshot.capabilities
                if (!capabilities.hasBundledGoService || !capabilities.hasBundledMaaFramework) {
                    failRun(taskId, "Bundled Maa runtime missing. Stage runtime/agent/go-service and runtime/maafw first.")
                    false
                } else {
                    runAndroidNativeTask(taskId, optionOverrideJson)
                }
            }
        }
    }

    private fun completeRun(taskId: String?, message: String, phase: RunSessionPhase) {
        log(message)
        updateSnapshot {
            it.copy(
                phase = phase,
                currentTaskId = taskId,
                lastMessage = message,
                recentLogs = logger?.tail() ?: emptyList(),
            )
        }
    }

    private fun failRun(taskId: String?, message: String) {
        log(message)
        val screenshotPath = captureFailureScreenshot(taskId)
        updateSnapshot {
            it.copy(
                phase = RunSessionPhase.Failed,
                currentTaskId = taskId,
                lastMessage = message,
                lastFailure = FailureArtifact(
                    taskId = taskId,
                    screenshotPath = screenshotPath,
                    occurredAt = System.currentTimeMillis(),
                ),
                recentLogs = logger?.tail() ?: emptyList(),
            )
        }
    }

    private fun captureFailureScreenshot(taskId: String?): String? {
        val root = runtimeRoot ?: return null
        val screenshotsDir = File(root, "diagnostics").apply { mkdirs() }
        val screenshot = File(
            screenshotsDir,
            "failure-${taskId ?: "unknown"}-${System.currentTimeMillis()}.png",
        )
        val previewCaptured = runCatching {
            val bitmap = NativeBridgeLib.capturePreviewFrame() ?: return@runCatching false
            try {
                saveBitmap(bitmap, screenshot)
            } finally {
                bitmap.recycle()
            }
        }.getOrDefault(false)
        return if (previewCaptured || runShellCommand("/system/bin/screencap -p ${screenshot.absolutePath}")) {
            screenshot.absolutePath
        } else {
            null
        }
    }

    private fun saveBitmap(bitmap: Bitmap, destination: File): Boolean {
        return runCatching {
            destination.outputStream().buffered().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
        }.getOrDefault(false)
    }

    private fun runShellCommand(command: String): Boolean {
        return runCatching {
            val process = ProcessBuilder("/system/bin/sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val code = process.waitFor()
            if (output.isNotBlank()) {
                log(output.trim())
            }
            code == 0
        }.getOrElse {
            log("Shell command failed: ${it.message}")
            false
        }
    }

    private fun updateSnapshot(transform: (RuntimeStateSnapshot) -> RuntimeStateSnapshot) {
        synchronized(stateLock) {
            snapshot = transform(snapshot)
        }
    }

    private fun log(message: String) {
        logger?.log(message)
    }

    private inline fun dispatchWindowTouch(
        x: Int,
        y: Int,
        block: (x: Int, y: Int, displayId: Int) -> Boolean,
    ): Boolean {
        val displayId = VirtualDisplayManager.getDisplayId()
        if (displayId == DefaultDisplayConfig.DISPLAY_NONE) {
            return false
        }
        val tx = x.coerceIn(0, DefaultDisplayConfig.WIDTH - 1)
        val ty = y.coerceIn(0, DefaultDisplayConfig.HEIGHT - 1)
        return block(tx, ty, displayId)
    }

    private fun runtimeRootDirectory(): File {
        return RuntimeBootstrapper.defaultRuntimeRoot(context)
    }

    private fun runAndroidNativeTask(taskId: String, optionOverrideJson: String?): Boolean {
        val entry = resolveTaskEntry(taskId)
        if (entry == null) {
            failRun(taskId, "Task entry not found for $taskId")
            return false
        }

        val overrideJson = mergeOverrideJson(buildAndroidTaskOverride(taskId), optionOverrideJson)
        log("runAndroidNativeTask start: taskId=$taskId, entry=$entry, override=$overrideJson")
        return runCatching {
            val bridge = MaaFrameworkBridge().also {
                it.init(context, runtimeRoot ?: runtimeRootDirectory())
            }
            maaBridge?.destroy()
            maaBridge = bridge
            log("Loaded MaaFramework ${bridge.version()}")
            if (taskId != "AndroidOpenGame") {
                ensureGameReady(bridge)
                normalizeSceneForTask(taskId)
            }
            val result = bridge.runTask(entry, overrideJson)
            log("runAndroidNativeTask result: success=${result.success}, message=${result.message}")
            if (!result.success) {
                error(result.message)
            }
            true
        }.getOrElse {
            failRun(taskId, it.message ?: "Android native task failed")
            false
        }
    }

    private fun runAndroidOpenGameTask(optionOverrideJson: String?): Boolean {
        log("runAndroidOpenGameTask start")
        return runCatching {
            ensureWindowedDisplay()
            val bridge = MaaFrameworkBridge().also {
                it.init(context, runtimeRoot ?: runtimeRootDirectory())
            }
            maaBridge?.destroy()
            maaBridge = bridge
            log("Loaded MaaFramework ${bridge.version()}")
            val entry = resolveTaskEntry("AndroidOpenGame")
                ?: error("Task entry not found for AndroidOpenGame")
            val overrideJson = optionOverrideJson ?: "{}"
            val result = bridge.runTask(entry, overrideJson)
            log("runAndroidOpenGameTask result: success=${result.success}, message=${result.message}")
            check(result.success) { result.message.ifBlank { "AndroidOpenGame failed" } }
            preparedGameDisplayId = VirtualDisplayManager.getDisplayId()
            true
        }.getOrElse {
            preparedGameDisplayId = DefaultDisplayConfig.DISPLAY_NONE
            failRun("AndroidOpenGame", it.message ?: "AndroidOpenGame failed")
            false
        }
    }

    private fun ensureGameReady(
        bridge: MaaFrameworkBridge,
        optionOverrideJson: String? = null,
    ) {
        val targetDisplayId = ensureWindowedDisplay()
        val packageName = "com.hypergryph.endfield"
        val runningDisplayId = getRunningPackageDisplayId(packageName)
        val alreadyPrepared =
            preparedGameDisplayId == targetDisplayId &&
                runningDisplayId == targetDisplayId
        if (alreadyPrepared) {
            log("preflight AndroidOpenGame skipped: game already prepared on display=$targetDisplayId")
            return
        }
        val entry = resolveTaskEntry("AndroidOpenGame")
            ?: error("Task entry not found for AndroidOpenGame")
        val overrideJson = optionOverrideJson ?: "{}"
        val result = bridge.runTask(entry, overrideJson)
        log("preflight AndroidOpenGame: success=${result.success}, message=${result.message}")
        check(result.success) { "preflight AndroidOpenGame failed: ${result.message}" }
        preparedGameDisplayId = targetDisplayId
    }

    private fun ensureWindowedDisplay(): Int {
        val existingDisplayId = VirtualDisplayManager.getDisplayId()
        if (existingDisplayId != DefaultDisplayConfig.DISPLAY_NONE) {
            return existingDisplayId
        }

        val startedDisplayId = VirtualDisplayManager.start(context)
        check(startedDisplayId != DefaultDisplayConfig.DISPLAY_NONE) { "failed to start virtual display" }
        log("windowed display ready: display=$startedDisplayId")
        return startedDisplayId
    }

    private fun normalizeSceneForTask(taskId: String) {
        return
    }

    private fun getRunningPackageDisplayId(packageName: String): Int? {
        val command = """
            dumpsys activity activities | awk '
                /Display #[0-9]+/ {
                    display = $2;
                    gsub(":", "", display);
                    gsub("#", "", display);
                }
                $0 ~ /packageName=$packageName/ {
                    print display;
                    exit;
                }
            '
        """.trimIndent()
        return runCatching {
            val process = ProcessBuilder("/system/bin/sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText().trim() }
            process.waitFor()
            output.toIntOrNull()
        }.getOrNull()
    }

    private fun resolveTaskEntry(taskId: String): String? {
        return runCatching { catalogLoader.load() }
            .getOrNull()
            ?.tasks
            ?.firstOrNull { it.id == taskId }
            ?.entry
            ?.takeIf { it.isNotBlank() }
    }

    private fun buildAndroidTaskOverride(taskId: String): String {
        if (taskId != "DailyRewards") {
            return "{}"
        }
        return """
            {
              "DailyEmailRewardSub": { "enabled": false },
              "DailyEventRewardSub": { "enabled": false },
              "DailyTaskRewardSub": { "enabled": false },
              "DailyProtocolPassRewardSub": { "enabled": false },
              "DailyClaimDeliveryJobsRewardSub": { "enabled": true }
            }
        """.trimIndent()
    }

    private fun mergeOverrideJson(baseJson: String, extraJson: String?): String {
        if (extraJson.isNullOrBlank() || extraJson == "{}") {
            return baseJson
        }
        if (baseJson.isBlank() || baseJson == "{}") {
            return extraJson
        }

        val base = runCatching { json.decodeFromString<JsonObject>(baseJson) }.getOrDefault(JsonObject(emptyMap()))
        val extra = runCatching { json.decodeFromString<JsonObject>(extraJson) }.getOrDefault(JsonObject(emptyMap()))
        return mergeJsonObjects(base, extra).toString()
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

    private companion object {
        const val TAG = "RootRuntimeService"
    }
}

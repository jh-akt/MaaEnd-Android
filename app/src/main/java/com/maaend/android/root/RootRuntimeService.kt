package com.maaend.android.root

import android.content.Context
import com.maaend.android.ipc.IRootRuntimeService
import com.maaend.android.model.FailureArtifact
import com.maaend.android.model.RunRequest
import com.maaend.android.model.RunSessionPhase
import com.maaend.android.model.RuntimeStateSnapshot
import com.maaend.android.runtime.RuntimeBootstrapper
import com.maaend.android.runtime.RuntimeLogger
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.system.exitProcess

class RootRuntimeService(
    private val context: Context,
) : IRootRuntimeService.Stub() {
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
    private var snapshot = RuntimeStateSnapshot(
        lastMessage = "Root runtime bootstrapped",
    )

    override fun ping(): String {
        return buildString {
            append("MaaEnd Android Root Runtime")
            append(" | prepared=")
            append(snapshot.runtimePrepared)
            append(" | root=")
            append(context.filesDir.absolutePath)
        }
    }

    override fun prepareRuntime(): Boolean {
        updateSnapshot { it.copy(phase = RunSessionPhase.Preparing, lastMessage = "Preparing runtime") }
        return runCatching {
            val root = File(context.filesDir, "maaend-runtime/v1")
            val runtimeLogger = RuntimeLogger(root)
            logger = runtimeLogger
            val result = RuntimeBootstrapper.prepare(context, runtimeLogger)
            runtimeRoot = result.runtimeRoot

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

        stopRequested = false
        currentFuture = executor.submit {
            val tasks = request.sequenceTaskIds.ifEmpty {
                request.taskId?.let(::listOf) ?: emptyList()
            }

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
                if (!runSingleTask(taskId)) {
                    return@submit
                }
            }
            completeRun(tasks.last(), "Run completed", RunSessionPhase.Completed)
        }
        return true
    }

    override fun stopRun() {
        stopRequested = true
        updateSnapshot { it.copy(phase = RunSessionPhase.Stopping, lastMessage = "Stop requested") }
        currentFuture?.cancel(true)
        updateSnapshot { it.copy(phase = RunSessionPhase.Idle, currentTaskId = null, lastMessage = "Run stopped") }
    }

    override fun getState(): String {
        val recentLogs = logger?.tail() ?: emptyList()
        return json.encodeToString(snapshot.copy(recentLogs = recentLogs))
    }

    override fun exportDiagnostics(): String {
        val root = runtimeRoot ?: File(context.filesDir, "maaend-runtime/v1")
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
        currentFuture?.cancel(true)
        executor.shutdownNow()
        exitProcess(0)
    }

    private fun runSingleTask(taskId: String): Boolean {
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
                val success = runShellCommand(
                    "/system/bin/monkey -p com.hypergryph.endfield -c android.intent.category.LAUNCHER 1",
                )
                if (!success) {
                    failRun(taskId, "Failed to launch com.hypergryph.endfield")
                    false
                } else {
                    log("AndroidOpenGame fallback launch succeeded")
                    true
                }
            }

            else -> {
                val capabilities = snapshot.capabilities
                val message = if (!capabilities.hasBundledGoService || !capabilities.hasBundledMaaFramework) {
                    "Bundled Maa runtime missing. Stage runtime/agent/go-service and runtime/maafw first."
                } else {
                    "Maa runtime bridge for task execution is scaffolded but not wired in this build."
                }
                failRun(taskId, message)
                false
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
        return if (runShellCommand("/system/bin/screencap -p ${screenshot.absolutePath}")) {
            screenshot.absolutePath
        } else {
            null
        }
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
}

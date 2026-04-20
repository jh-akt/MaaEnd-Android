package com.maaend.android.runtime

import android.content.Context
import android.content.res.AssetManager
import com.maaend.android.model.RuntimeCapabilities
import java.io.File

data class RuntimePrepareResult(
    val runtimeRoot: File,
    val capabilities: RuntimeCapabilities,
    val message: String,
)

object RuntimeBootstrapper {
    private const val RUNTIME_VERSION = "v1"
    private val REQUIRED_ASSET_ENTRIES = listOf(
        "interface.json",
        "locales",
        "tasks",
        "resource",
        "resource_adb",
    )

    fun prepare(
        context: Context,
        logger: RuntimeLogger,
        runtimeRoot: File = defaultRuntimeRoot(context),
    ): RuntimePrepareResult {
        val assets = context.assets

        runtimeRoot.mkdirs()
        File(runtimeRoot, "logs").mkdirs()
        File(runtimeRoot, "diagnostics").mkdirs()
        stopStaleAgentProcesses(runtimeRoot, logger)
        resetRuntimePayload(runtimeRoot, logger)

        REQUIRED_ASSET_ENTRIES.forEach { entry ->
            copyAssetEntry(assets, entry, File(runtimeRoot, entry), logger)
        }

        if (assetEntryExists(assets, "bundled_runtime")) {
            copyAssetEntry(assets, "bundled_runtime", runtimeRoot, logger)
        }
        overlayBundledPrivatePipeline(runtimeRoot, logger)
        overlayBundledResourceAdb(runtimeRoot, logger)

        val goService = File(runtimeRoot, "agent/go-service")
        val maafwDir = File(runtimeRoot, "maafw")
        goService.parentFile?.mkdirs()
        goService.setExecutable(true, false)

        val capabilities = RuntimeCapabilities(
            hasBundledGoService = goService.exists(),
            hasBundledMaaFramework = maafwDir.exists() && maafwDir.list()?.isNotEmpty() == true,
            canFallbackOpenGame = true,
        )

        val message = buildString {
            append("Runtime prepared")
            if (!capabilities.hasBundledGoService || !capabilities.hasBundledMaaFramework) {
                append(" with missing bundled Maa runtime components")
            }
        }

        logger.log(message)

        return RuntimePrepareResult(
            runtimeRoot = runtimeRoot,
            capabilities = capabilities,
            message = message,
        )
    }

    private fun assetEntryExists(assets: AssetManager, path: String): Boolean {
        return try {
            val entries = assets.list(path)
            if (entries != null && entries.isNotEmpty()) {
                true
            } else {
                assets.open(path).close()
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun copyAssetEntry(
        assets: AssetManager,
        assetPath: String,
        target: File,
        logger: RuntimeLogger,
    ) {
        val children = try {
            assets.list(assetPath) ?: emptyArray()
        } catch (_: Throwable) {
            emptyArray()
        }

        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            assets.open(assetPath).use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return
        }

        target.mkdirs()
        for (child in children) {
            copyAssetEntry(
                assets = assets,
                assetPath = "$assetPath/$child",
                target = File(target, child),
                logger = logger,
            )
        }
        logger.log("Extracted asset directory: $assetPath")
    }

    fun defaultRuntimeRoot(context: Context): File {
        return File("/data/local/tmp/${context.packageName}/maaend-runtime/$RUNTIME_VERSION")
    }

    private fun resetRuntimePayload(runtimeRoot: File, logger: RuntimeLogger) {
        val staleEntries = listOf(
            "interface.json",
            "locales",
            "tasks",
            "resource",
            "resource_adb",
            "agent",
            "maafw",
            "MaaPiCli",
            "bundled_runtime",
        )
        staleEntries.forEach { name ->
            deleteRecursively(File(runtimeRoot, name))
        }
        deleteRecursively(File(runtimeRoot, "maafw/plugins.disabled"))
        logger.log("Cleared stale runtime payload before prepare")
    }

    private fun overlayBundledResourceAdb(runtimeRoot: File, logger: RuntimeLogger) {
        val overlayRoot = File(runtimeRoot, "bundled_runtime/resource_adb")
        val targetRoot = File(runtimeRoot, "resource_adb")
        if (!overlayRoot.exists()) {
            return
        }
        copyDirectoryContents(overlayRoot, targetRoot)
        logger.log("Overlayed bundled_runtime/resource_adb into resource_adb")
    }

    private fun overlayBundledPrivatePipeline(runtimeRoot: File, logger: RuntimeLogger) {
        val overlayRoot = File(runtimeRoot, "private_pipeline")
        if (!overlayRoot.exists()) {
            return
        }

        val resourcePrivateRoot = File(overlayRoot, "resource/CommonPrivate")
        if (resourcePrivateRoot.exists()) {
            copyDirectoryContents(
                sourceRoot = resourcePrivateRoot,
                targetRoot = File(runtimeRoot, "resource/pipeline/Common/__Private"),
            )
            logger.log("Overlayed private_pipeline into resource/pipeline/Common/__Private")
        }

        val resourceAdbPrivateRoot = File(overlayRoot, "resource_adb/CommonPrivate")
        if (resourceAdbPrivateRoot.exists()) {
            copyDirectoryContents(
                sourceRoot = resourceAdbPrivateRoot,
                targetRoot = File(runtimeRoot, "resource_adb/pipeline/Common/__Private"),
            )
            logger.log("Overlayed private_pipeline into resource_adb/pipeline/Common/__Private")
        }
    }

    private fun copyDirectoryContents(sourceRoot: File, targetRoot: File) {
        sourceRoot.walkTopDown().forEach { file ->
            val relative = file.relativeTo(sourceRoot)
            val target = File(targetRoot, relative.path)
            if (file.isDirectory) {
                target.mkdirs()
            } else {
                target.parentFile?.mkdirs()
                file.inputStream().use { input ->
                    target.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    private fun stopStaleAgentProcesses(runtimeRoot: File, logger: RuntimeLogger) {
        val agentPath = File(runtimeRoot, "agent/go-service").absolutePath
        val command = "pkill -f '$agentPath' || true"
        runCatching {
            val process = ProcessBuilder("/system/bin/sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText().trim() }
            val code = process.waitFor()
            if (output.isNotBlank()) {
                logger.log(output)
            }
            logger.log("Stopped stale go-service processes before prepare: exit=$code")
        }.onFailure { error ->
            logger.log("Failed to stop stale go-service processes: ${error.message}")
        }
    }

    private fun deleteRecursively(file: File) {
        if (!file.exists()) {
            return
        }
        if (file.isDirectory) {
            file.listFiles()?.forEach(::deleteRecursively)
        }
        file.delete()
    }
}

class RuntimeLogger(runtimeRoot: File) {
    private val logFile = File(runtimeRoot, "logs/root-runtime.log").apply {
        parentFile?.mkdirs()
        if (!exists()) {
            createNewFile()
        }
    }

    @Synchronized
    fun log(message: String) {
        logFile.appendText("${System.currentTimeMillis()} $message\n")
    }

    @Synchronized
    fun tail(maxLines: Int = 120): List<String> {
        return logFile.takeIf { it.exists() }
            ?.readLines()
            ?.takeLast(maxLines)
            ?: emptyList()
    }

    fun file(): File = logFile
}

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

    fun prepare(context: Context, logger: RuntimeLogger): RuntimePrepareResult {
        val runtimeRoot = File(context.filesDir, "maaend-runtime/$RUNTIME_VERSION")
        val assets = context.assets

        runtimeRoot.mkdirs()
        File(runtimeRoot, "logs").mkdirs()
        File(runtimeRoot, "diagnostics").mkdirs()

        REQUIRED_ASSET_ENTRIES.forEach { entry ->
            copyAssetEntry(assets, entry, File(runtimeRoot, entry), logger)
        }

        if (assetEntryExists(assets, "bundled_runtime")) {
            copyAssetEntry(assets, "bundled_runtime", runtimeRoot, logger)
        }

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

package com.maaend.android.runtime

import android.content.Context
import com.maaframework.android.project.MaaProjectManifest
import com.maaframework.android.project.MaaProjectManifestLoader
import com.maaframework.android.runtime.PersistentProjectRepositoryManager
import com.maaframework.android.runtime.PersistentProjectRepositorySyncProgress
import com.maaframework.android.runtime.PersistentProjectRepositoryStatus
import java.io.File
import kotlinx.serialization.Serializable

@Serializable
data class PersistentResourceRepositoryStatus(
    val available: Boolean = false,
    val source: String = "github",
    val branch: String = "v2",
    val mainRevision: String? = null,
    val modelRevision: String? = null,
    val updatedAt: Long = 0L,
    val rootPath: String? = null,
    val lastError: String? = null,
)

data class PersistentResourceRepositorySyncProgress(
    val fraction: Float = 0f,
    val label: String = "",
)

object PersistentResourceRepositoryManager {
    fun currentRoot(context: Context): File {
        return PersistentProjectRepositoryManager.currentRoot(context, loadManifest(context))
    }

    fun loadStatus(context: Context): PersistentResourceRepositoryStatus {
        val manifest = loadManifest(context)
        return PersistentProjectRepositoryManager.loadStatus(context, manifest)
            .toMaaEndStatus(manifest)
    }

    fun requireCurrentRoot(context: Context): File {
        val status = loadStatus(context)
        check(status.available) {
            status.lastError?.let { "MaaEnd 资源仓库不可用：$it" }
                ?: "MaaEnd 资源仓库未就绪，请先在设置中同步 GitHub 资源"
        }
        return currentRoot(context)
    }

    fun ensureAvailable(
        context: Context,
        logger: ((String) -> Unit)? = null,
        progress: ((PersistentResourceRepositorySyncProgress) -> Unit)? = null,
    ): PersistentResourceRepositoryStatus {
        val manifest = loadManifest(context)
        return PersistentProjectRepositoryManager.ensureAvailable(
            context = context,
            manifest = manifest,
            logger = logger,
            progress = progress?.let { callback ->
                { next -> callback(next.toMaaEndProgress()) }
            },
        ).toMaaEndStatus(manifest)
    }

    fun clearLocalCache(context: Context): PersistentResourceRepositoryStatus {
        runCatching {
            val baseDir = currentRoot(context).parentFile
            if (baseDir != null && baseDir.exists()) {
                deleteRecursively(baseDir)
            }
        }
        return loadStatus(context)
    }

    fun updateFromGithub(
        context: Context,
        logger: ((String) -> Unit)? = null,
        progress: ((PersistentResourceRepositorySyncProgress) -> Unit)? = null,
    ): PersistentResourceRepositoryStatus {
        val manifest = loadManifest(context)
        return PersistentProjectRepositoryManager.updateFromGithub(
            context = context,
            manifest = manifest,
            logger = logger,
            progress = progress?.let { callback ->
                { next -> callback(next.toMaaEndProgress()) }
            },
        ).toMaaEndStatus(manifest)
    }

    private fun loadManifest(context: Context): MaaProjectManifest {
        return MaaProjectManifestLoader.loadOrDefault(context.assets)
    }

    private fun PersistentProjectRepositoryStatus.toMaaEndStatus(
        manifest: MaaProjectManifest,
    ): PersistentResourceRepositoryStatus {
        val config = manifest.githubResourceRepository
        return PersistentResourceRepositoryStatus(
            available = available,
            source = source,
            branch = branch ?: config?.branch ?: "v2",
            mainRevision = mainRevision,
            modelRevision = null,
            updatedAt = updatedAt,
            rootPath = rootPath,
            lastError = lastError,
        )
    }

    private fun PersistentProjectRepositorySyncProgress.toMaaEndProgress(): PersistentResourceRepositorySyncProgress {
        return PersistentResourceRepositorySyncProgress(
            fraction = fraction,
            label = label,
        )
    }

    private fun deleteRecursively(file: File) {
        if (!file.exists()) {
            return
        }
        if (file.isDirectory) {
            file.listFiles()?.forEach(::deleteRecursively)
        }
        check(file.delete() || !file.exists()) {
            "Failed to delete ${file.absolutePath}"
        }
    }
}

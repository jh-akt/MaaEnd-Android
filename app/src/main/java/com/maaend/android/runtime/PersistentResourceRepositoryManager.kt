package com.maaend.android.runtime

import android.content.Context
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class PersistentResourceRepositoryStatus(
    val available: Boolean = false,
    val source: String = "github",
    val branch: String = MaaEndRemoteConfig.BRANCH,
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

private object MaaEndRemoteConfig {
    const val OWNER = "MaaEnd"
    const val REPO = "MaaEnd"
    const val BRANCH = "v2"
    const val MODEL_SUBMODULE_PATH = "assets/resource/model"
    const val USER_AGENT = "MaaEnd-Android/0.1"

    fun repoZipUrl(branch: String): String =
        "https://codeload.github.com/$OWNER/$REPO/zip/refs/heads/$branch"

    fun modelSubmoduleApiUrl(branch: String): String =
        "https://api.github.com/repos/$OWNER/$REPO/contents/$MODEL_SUBMODULE_PATH?ref=$branch"

    fun repoApiUrl(owner: String, repo: String, revision: String): String =
        "https://codeload.github.com/$owner/$repo/zip/$revision"
}

object PersistentResourceRepositoryManager {
    private const val META_FILE_NAME = ".maaend-resource.json"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun currentRoot(context: Context): File {
        return File(sharedBaseDir(context), "current")
    }

    fun loadStatus(context: Context): PersistentResourceRepositoryStatus {
        migrateLegacyInternalRepositoryIfNeeded(context)
        val root = currentRoot(context)
        val meta = readMetadata(root)
        val ready = isRepositoryReady(root)
        return if (ready) {
            (meta ?: PersistentResourceRepositoryStatus(
                available = true,
                source = "github",
                rootPath = root.absolutePath,
            )).copy(
                available = true,
                rootPath = root.absolutePath,
                lastError = null,
            )
        } else {
            PersistentResourceRepositoryStatus(
                available = false,
                source = meta?.source ?: "github",
                branch = meta?.branch ?: MaaEndRemoteConfig.BRANCH,
                rootPath = root.absolutePath,
                lastError = meta?.lastError,
            )
        }
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
        val existing = loadStatus(context)
        if (existing.available) {
            return existing
        }
        return updateFromGithub(context, logger, progress)
    }

    fun clearLocalCache(context: Context): PersistentResourceRepositoryStatus {
        clearRepositoryStorage(
            sharedBaseDir = sharedBaseDir(context),
            legacyInternalBaseDir = legacyInternalBaseDir(context),
        )
        return loadStatus(context)
    }

    fun updateFromGithub(
        context: Context,
        logger: ((String) -> Unit)? = null,
        progress: ((PersistentResourceRepositorySyncProgress) -> Unit)? = null,
    ): PersistentResourceRepositoryStatus {
        val baseDir = sharedBaseDir(context).apply { mkdirs() }
        val currentRoot = currentRoot(context)
        val stagingRoot = File(baseDir, "staging-${System.currentTimeMillis()}").apply { mkdirs() }
        val previousRoot = File(baseDir, "previous")

        return runCatching {
            reportProgress(progress, 0.04f, "准备同步 GitHub 资源")
            logger?.invoke("Downloading MaaEnd resource repository from GitHub")
            reportProgress(progress, 0.12f, "正在解析 MaaEnd-AI 版本")
            val modelSubmodule = fetchModelSubmodule(logger)
            downloadAndExtractMainRepository(stagingRoot, logger, progress)
            downloadAndExtractModelRepository(
                targetRoot = File(stagingRoot, "resource/model"),
                submodule = modelSubmodule,
                logger = logger,
                progress = progress,
            )

            reportProgress(progress, 0.90f, "正在写入本地缓存")
            val status = PersistentResourceRepositoryStatus(
                available = true,
                source = "github",
                branch = MaaEndRemoteConfig.BRANCH,
                mainRevision = MaaEndRemoteConfig.BRANCH,
                modelRevision = modelSubmodule.revision,
                updatedAt = System.currentTimeMillis(),
                rootPath = currentRoot.absolutePath,
            )
            writeMetadata(stagingRoot, status)

            if (previousRoot.exists()) {
                deleteRecursively(previousRoot)
            }
            if (currentRoot.exists()) {
                currentRoot.renameTo(previousRoot)
            }
            if (!stagingRoot.renameTo(currentRoot)) {
                copyDirectoryContents(stagingRoot, currentRoot)
                deleteRecursively(stagingRoot)
            }
            deleteRecursively(previousRoot)

            reportProgress(progress, 1f, "GitHub 资源同步完成")
            logger?.invoke("Persistent GitHub resource repository updated")
            loadStatus(context)
        }.getOrElse { error ->
            val message = error.message ?: error::class.java.simpleName
            logger?.invoke("GitHub resource repository update failed: $message")
            deleteRecursively(stagingRoot)
            val existing = loadStatus(context)
            existing.copy(
                lastError = message,
            )
        }
    }

    private fun sharedBaseDir(context: Context): File {
        val externalRoot = resolveExternalFilesRoot(context.packageName) {
            context.getExternalFilesDir(null)
        }
        return File(externalRoot, "maaend-resource")
    }

    internal fun resolveExternalFilesRoot(
        packageName: String,
        externalFilesDirProvider: () -> File?,
    ): File {
        return runCatching { externalFilesDirProvider() }.getOrNull()
            ?: File("/sdcard/Android/data/$packageName/files")
    }

    private fun legacyInternalCurrentRoot(context: Context): File {
        return File(legacyInternalBaseDir(context), "current")
    }

    private fun legacyInternalBaseDir(context: Context): File {
        return File(context.filesDir, "maaend-resource")
    }

    private fun migrateLegacyInternalRepositoryIfNeeded(context: Context) {
        val targetRoot = currentRoot(context)
        if (isRepositoryReady(targetRoot)) {
            return
        }

        val legacyRoot = legacyInternalCurrentRoot(context)
        if (!isRepositoryReady(legacyRoot)) {
            return
        }

        runCatching {
            val targetBaseDir = sharedBaseDir(context).apply { mkdirs() }
            val stagingRoot = File(targetBaseDir, "migration-${System.currentTimeMillis()}").apply { mkdirs() }
            copyDirectoryContents(legacyRoot, stagingRoot)
            if (targetRoot.exists()) {
                deleteRecursively(targetRoot)
            }
            if (!stagingRoot.renameTo(targetRoot)) {
                copyDirectoryContents(stagingRoot, targetRoot)
                deleteRecursively(stagingRoot)
            }
        }
    }

    private fun downloadAndExtractMainRepository(
        targetRoot: File,
        logger: ((String) -> Unit)?,
        progress: ((PersistentResourceRepositorySyncProgress) -> Unit)?,
    ) {
        reportProgress(progress, 0.18f, "正在下载 MaaEnd 主资源")
        val zipFile = downloadToTempFile(
            url = MaaEndRemoteConfig.repoZipUrl(MaaEndRemoteConfig.BRANCH),
            prefix = "maaend-main",
            logger = logger,
            onProgress = { fraction ->
                reportProgress(progress, 0.18f + (fraction * 0.24f), "正在下载 MaaEnd 主资源")
            },
        )
        try {
            reportProgress(progress, 0.45f, "正在解压 MaaEnd 主资源")
            extractZip(zipFile, targetRoot) { entryName ->
                val marker = "/assets/"
                val index = entryName.indexOf(marker)
                if (index < 0) {
                    null
                } else {
                    entryName.substring(index + marker.length).takeIf { it.isNotBlank() }
                }
            }
        } finally {
            zipFile.delete()
        }
    }

    private fun downloadAndExtractModelRepository(
        targetRoot: File,
        submodule: GitSubmoduleInfo,
        logger: ((String) -> Unit)?,
        progress: ((PersistentResourceRepositorySyncProgress) -> Unit)?,
    ) {
        reportProgress(progress, 0.58f, "正在下载 MaaEnd-AI 资源")
        val zipFile = downloadToTempFile(
            url = MaaEndRemoteConfig.repoApiUrl(submodule.owner, submodule.repo, submodule.revision),
            prefix = "maaend-model",
            logger = logger,
            onProgress = { fraction ->
                reportProgress(progress, 0.58f + (fraction * 0.18f), "正在下载 MaaEnd-AI 资源")
            },
        )
        try {
            reportProgress(progress, 0.79f, "正在解压 MaaEnd-AI 资源")
            extractZip(zipFile, targetRoot) { entryName ->
                entryName.substringAfter('/', "").takeIf { it.isNotBlank() }
            }
        } finally {
            zipFile.delete()
        }
    }

    private fun fetchModelSubmodule(logger: ((String) -> Unit)?): GitSubmoduleInfo {
        logger?.invoke("Resolving MaaEnd-AI submodule revision from MaaEnd GitHub API")
        val connection = openConnection(MaaEndRemoteConfig.modelSubmoduleApiUrl(MaaEndRemoteConfig.BRANCH))
        connection.inputStream.bufferedReader().use { reader ->
            val root = json.parseToJsonElement(reader.readText()).jsonObject
            val revision = root["sha"]?.jsonPrimitive?.content
                ?: error("Missing submodule revision for ${MaaEndRemoteConfig.MODEL_SUBMODULE_PATH}")
            val submoduleUrl = root["submodule_git_url"]?.jsonPrimitive?.content
                ?: "https://github.com/MaaEnd/MaaEnd-AI.git"
            return GitSubmoduleInfo.fromUrl(submoduleUrl, revision)
        }
    }

    private fun downloadToTempFile(
        url: String,
        prefix: String,
        logger: ((String) -> Unit)?,
        onProgress: ((Float) -> Unit)? = null,
    ): File {
        logger?.invoke("Downloading $url")
        val tempFile = File.createTempFile(prefix, ".zip")
        val connection = openConnection(url)
        val totalBytes = connection.contentLengthLong.takeIf { it > 0L }
        connection.inputStream.use { input ->
            FileOutputStream(tempFile).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var downloadedBytes = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) {
                        break
                    }
                    output.write(buffer, 0, read)
                    downloadedBytes += read
                    totalBytes?.let { total ->
                        onProgress?.invoke((downloadedBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                    }
                }
            }
        }
        onProgress?.invoke(1f)
        return tempFile
    }

    private fun reportProgress(
        progress: ((PersistentResourceRepositorySyncProgress) -> Unit)?,
        fraction: Float,
        label: String,
    ) {
        progress?.invoke(
            PersistentResourceRepositorySyncProgress(
                fraction = fraction.coerceIn(0f, 1f),
                label = label,
            ),
        )
    }

    private fun openConnection(url: String): HttpURLConnection {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", MaaEndRemoteConfig.USER_AGENT)
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        val code = connection.responseCode
        if (code !in 200..299) {
            val body = runCatching { connection.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
            connection.disconnect()
            error("HTTP $code for $url${body?.let { ": $it" } ?: ""}")
        }
        return connection
    }

    private fun extractZip(
        zipFile: File,
        targetRoot: File,
        pathMapper: (String) -> String?,
    ) {
        targetRoot.mkdirs()
        ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val relativePath = pathMapper(entry.name)
                if (relativePath.isNullOrBlank()) {
                    zip.closeEntry()
                    continue
                }
                val output = File(targetRoot, relativePath)
                if (entry.isDirectory) {
                    output.mkdirs()
                } else {
                    output.parentFile?.mkdirs()
                    output.outputStream().use { stream ->
                        zip.copyTo(stream)
                    }
                }
                zip.closeEntry()
            }
        }
    }

    private fun readMetadata(root: File): PersistentResourceRepositoryStatus? {
        val file = File(root, META_FILE_NAME)
        if (!file.exists()) {
            return null
        }
        return runCatching {
            json.decodeFromString<PersistentResourceRepositoryStatus>(file.readText())
        }.getOrNull()
    }

    private fun writeMetadata(root: File, status: PersistentResourceRepositoryStatus) {
        File(root, META_FILE_NAME).writeText(json.encodeToString(status))
    }

    private fun isRepositoryReady(root: File): Boolean {
        if (!root.isDirectory) {
            return false
        }
        val required = listOf(
            "interface.json",
            "tasks",
            "locales",
            "resource",
            "resource_adb",
            "resource/model",
        )
        return required.all { File(root, it).exists() }
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

    internal fun clearRepositoryStorage(
        sharedBaseDir: File,
        legacyInternalBaseDir: File? = null,
    ) {
        deleteRecursively(sharedBaseDir)
        legacyInternalBaseDir?.let(::deleteRecursively)
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

private data class GitSubmoduleInfo(
    val owner: String,
    val repo: String,
    val revision: String,
) {
    companion object {
        fun fromUrl(url: String, revision: String): GitSubmoduleInfo {
            val normalized = url.removeSuffix(".git").trimEnd('/')
            val parts = normalized.substringAfter("github.com/").split('/')
            require(parts.size >= 2) { "Unsupported submodule url: $url" }
            return GitSubmoduleInfo(
                owner = parts[0],
                repo = parts[1],
                revision = revision,
            )
        }
    }
}

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
    val source: String = "assets",
    val branch: String = MaaEndRemoteConfig.BRANCH,
    val mainRevision: String? = null,
    val modelRevision: String? = null,
    val updatedAt: Long = 0L,
    val rootPath: String? = null,
    val lastError: String? = null,
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
        return File(context.filesDir, "maaend-resource/current")
    }

    fun loadStatus(context: Context): PersistentResourceRepositoryStatus {
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
                source = "assets",
                branch = meta?.branch ?: MaaEndRemoteConfig.BRANCH,
                lastError = meta?.lastError,
            )
        }
    }

    fun ensureAvailable(
        context: Context,
        logger: ((String) -> Unit)? = null,
    ): PersistentResourceRepositoryStatus {
        val existing = loadStatus(context)
        if (existing.available) {
            return existing
        }
        return updateFromGithub(context, logger)
    }

    fun updateFromGithub(
        context: Context,
        logger: ((String) -> Unit)? = null,
    ): PersistentResourceRepositoryStatus {
        val baseDir = File(context.filesDir, "maaend-resource").apply { mkdirs() }
        val currentRoot = currentRoot(context)
        val stagingRoot = File(baseDir, "staging-${System.currentTimeMillis()}").apply { mkdirs() }
        val previousRoot = File(baseDir, "previous")

        return runCatching {
            logger?.invoke("Downloading MaaEnd resource repository from GitHub")
            val modelSubmodule = fetchModelSubmodule(logger)
            downloadAndExtractMainRepository(stagingRoot, logger)
            downloadAndExtractModelRepository(
                targetRoot = File(stagingRoot, "resource/model"),
                submodule = modelSubmodule,
                logger = logger,
            )

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

            logger?.invoke("Persistent GitHub resource repository updated")
            loadStatus(context)
        }.getOrElse { error ->
            logger?.invoke("GitHub resource repository update failed: ${error.message}")
            deleteRecursively(stagingRoot)
            val existing = loadStatus(context)
            existing.copy(
                lastError = error.message ?: error::class.java.simpleName,
            )
        }
    }

    private fun downloadAndExtractMainRepository(
        targetRoot: File,
        logger: ((String) -> Unit)?,
    ) {
        val zipFile = downloadToTempFile(
            url = MaaEndRemoteConfig.repoZipUrl(MaaEndRemoteConfig.BRANCH),
            prefix = "maaend-main",
            logger = logger,
        )
        try {
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
    ) {
        val zipFile = downloadToTempFile(
            url = MaaEndRemoteConfig.repoApiUrl(submodule.owner, submodule.repo, submodule.revision),
            prefix = "maaend-model",
            logger = logger,
        )
        try {
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
    ): File {
        logger?.invoke("Downloading $url")
        val tempFile = File.createTempFile(prefix, ".zip")
        val connection = openConnection(url)
        connection.inputStream.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        return tempFile
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

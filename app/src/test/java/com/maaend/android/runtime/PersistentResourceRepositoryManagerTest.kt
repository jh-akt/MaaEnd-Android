package com.maaend.android.runtime

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentResourceRepositoryManagerTest {
    @Test
    fun `resolveExternalFilesRoot falls back when provider throws`() {
        val root = PersistentResourceRepositoryManager.resolveExternalFilesRoot(
            packageName = "com.maaend.android.debug",
        ) {
            error("callingPackage does not match UID")
        }

        assertEquals(
            "/sdcard/Android/data/com.maaend.android.debug/files",
            root.path,
        )
    }

    @Test
    fun `clearRepositoryStorage removes shared and legacy caches only`() {
        val root = Files.createTempDirectory("persistent-resource-repository-test").toFile()
        try {
            val sharedBaseDir = File(root, "external/maaend-resource").apply {
                resolve("current/interface.json").apply {
                    parentFile?.mkdirs()
                    writeText("{}")
                }
                resolve("previous/tasks/DailyRewards.json").apply {
                    parentFile?.mkdirs()
                    writeText("{}")
                }
            }
            val legacyBaseDir = File(root, "internal/maaend-resource").apply {
                resolve("current/locales/interface/zh_cn.json").apply {
                    parentFile?.mkdirs()
                    writeText("{}")
                }
            }
            val unrelatedFile = File(root, "keep/me.txt").apply {
                parentFile?.mkdirs()
                writeText("keep")
            }

            PersistentResourceRepositoryManager.clearRepositoryStorage(
                sharedBaseDir = sharedBaseDir,
                legacyInternalBaseDir = legacyBaseDir,
            )

            assertFalse(sharedBaseDir.exists())
            assertFalse(legacyBaseDir.exists())
            assertTrue(unrelatedFile.exists())
        } finally {
            root.deleteRecursively()
        }
    }
}

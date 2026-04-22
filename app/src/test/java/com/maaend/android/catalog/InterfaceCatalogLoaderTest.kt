package com.maaend.android.catalog

import com.maaend.android.model.TaskTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterfaceCatalogLoaderTest {
    private val loader = InterfaceCatalogLoader()

    @Test
    fun `parseCatalog keeps non pinned adb compatible tasks in lower section`() {
        val catalog = loader.parseCatalog(
            interfaceText = """
                {
                  "task": [
                    {
                      "name": "AndroidOpenGame",
                      "label": "${'$'}task.AndroidOpenGame.label",
                      "entry": "AndroidOpenGame"
                    },
                    {
                      "name": "CustomTask",
                      "label": "${'$'}task.CustomTask.label",
                      "entry": "CustomTaskEntry"
                    },
                    {
                      "name": "AdbTask",
                      "label": "${'$'}task.AdbTask.label",
                      "entry": "AdbTaskEntry",
                      "controller": ["ADB"]
                    },
                    {
                      "name": "DeliveryJobs",
                      "label": "${'$'}task.DeliveryJobs.label",
                      "entry": "DeliveryJobsEntry"
                    },
                    {
                      "name": "DailyRewards",
                      "label": "${'$'}task.DailyRewards.label",
                      "entry": "DailyRewardsEntry"
                    },
                    {
                      "name": "DesktopOnlyTask",
                      "label": "${'$'}task.DesktopOnlyTask.label",
                      "entry": "DesktopOnlyTaskEntry",
                      "controller": ["Win32-Front"]
                    }
                  ]
                }
            """.trimIndent(),
            localeText = """
                {
                  "task.AndroidOpenGame.label": "打开游戏",
                  "task.CustomTask.label": "自定义任务",
                  "task.AdbTask.label": "ADB 任务",
                  "task.DeliveryJobs.label": "转交委托",
                  "task.DailyRewards.label": "日常奖励领取",
                  "task.DesktopOnlyTask.label": "桌面任务"
                }
            """.trimIndent(),
            importResolver = { error("unexpected import") },
        )

        assertEquals(
            listOf("AndroidOpenGame", "DeliveryJobs", "DailyRewards", "AdbTask", "CustomTask"),
            catalog.tasks.map { it.id },
        )
        assertEquals(TaskTier.MVP, catalog.tasks.first { it.id == "AndroidOpenGame" }.tier)
        assertEquals(TaskTier.STRETCH, catalog.tasks.first { it.id == "DeliveryJobs" }.tier)
        assertEquals(TaskTier.STRETCH, catalog.tasks.first { it.id == "DailyRewards" }.tier)
        assertEquals(TaskTier.STRETCH, catalog.tasks.first { it.id == "AdbTask" }.tier)
        assertEquals(TaskTier.STRETCH, catalog.tasks.first { it.id == "CustomTask" }.tier)
        assertFalse(catalog.tasks.any { it.id == "DesktopOnlyTask" })
    }

    @Test
    fun `parseCatalog keeps non pinned task options when task stays visible`() {
        val catalog = loader.parseCatalog(
            interfaceText = """
                {
                  "task": [
                    {
                      "name": "CustomTask",
                      "label": "${'$'}task.CustomTask.label",
                      "entry": "CustomTaskEntry",
                      "option": ["SharedOption"]
                    }
                  ],
                  "option": {
                    "SharedOption": {
                      "type": "switch",
                      "label": "${'$'}option.SharedOption.label",
                      "default_case": "Yes",
                      "cases": [
                        { "name": "Yes" },
                        { "name": "No" }
                      ]
                    }
                  }
                }
            """.trimIndent(),
            localeText = """
                {
                  "task.CustomTask.label": "自定义任务",
                  "option.SharedOption.label": "共享配置"
                }
            """.trimIndent(),
            importResolver = { error("unexpected import") },
        )

        val task = catalog.tasks.single()
        assertEquals("CustomTask", task.id)
        assertEquals(TaskTier.STRETCH, task.tier)
        assertTrue(task.options.any { it.id == "SharedOption" })
    }
}

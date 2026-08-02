package com.maaend.android.ui

import com.maaframework.android.model.TaskDescriptor
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectInterfaceSupportTest {
    @Test
    fun `open game is inserted before selected work`() {
        val openGame = task(ProjectInterfaceSupport.OPEN_GAME_TASK_ID)
        val daily = task("DailyRewards")

        assertEquals(
            listOf(ProjectInterfaceSupport.OPEN_GAME_TASK_ID, "DailyRewards"),
            ProjectInterfaceSupport.ensureOpenGameFirst(
                tasks = listOf(daily),
                availableTasks = listOf(openGame, daily),
            ).map { it.id },
        )
    }

    private fun task(id: String) = TaskDescriptor(
        id = id,
        label = id,
        description = "",
        entry = id,
        groups = emptyList(),
        controllers = emptyList(),
    )
}

package com.maaend.android.model

object TaskSequenceSupport {
    const val OPEN_GAME_TASK_ID = "AndroidOpenGame"

    fun ensureOpenGameFirst(taskIds: List<String>): List<String> {
        if (taskIds.isEmpty()) {
            return emptyList()
        }
        val filtered = taskIds.filterNot { it == OPEN_GAME_TASK_ID }
        return listOf(OPEN_GAME_TASK_ID) + filtered
    }

    fun ensureOpenGameFirst(
        tasks: List<TaskDescriptor>,
        availableTasks: List<TaskDescriptor> = tasks,
    ): List<TaskDescriptor> {
        if (tasks.isEmpty()) {
            return emptyList()
        }

        val openGameTask = tasks.firstOrNull { it.id == OPEN_GAME_TASK_ID }
            ?: availableTasks.firstOrNull { it.id == OPEN_GAME_TASK_ID }
            ?: return tasks

        val filtered = tasks.filterNot { it.id == OPEN_GAME_TASK_ID }
        return listOf(openGameTask) + filtered
    }
}

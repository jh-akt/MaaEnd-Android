package com.maaend.android.ui

import com.maaframework.android.catalog.TaskOptionSupport
import com.maaframework.android.model.TaskDescriptor
import com.maaframework.android.model.TaskOptionDescriptor

object ProjectInterfaceSupport {
    const val GLOBAL_SCOPE_ID = "__global__"
    const val OPEN_GAME_TASK_ID = "AndroidOpenGame"

    val MVP_TASK_ORDER = listOf(
        OPEN_GAME_TASK_ID,
        "DijiangRewards",
        "CreditShoppingN2",
        "VisitFriends",
        "SellProduct",
    )

    val PINNED_TASK_ORDER = MVP_TASK_ORDER + listOf(
        "DeliveryJobs",
        "DailyRewards",
    )

    val PINNED_TASK_IDS = PINNED_TASK_ORDER.toSet()

    fun resourceScopeId(resourceId: String): String = "__resource__:$resourceId"

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

    fun taskSupportsResource(task: TaskDescriptor, resourceId: String?): Boolean {
        return TaskOptionSupport.taskSupportsResource(task, resourceId)
    }

    fun filterOptionsForResource(
        options: List<TaskOptionDescriptor>,
        resourceId: String?,
    ): List<TaskOptionDescriptor> {
        return TaskOptionSupport.filterOptionsForResource(options, resourceId)
    }

    fun collectInputValidationErrors(
        options: List<TaskOptionDescriptor>,
        selectedByOption: Map<String, Set<String>>,
        inputValuesByOption: Map<String, Map<String, String>>,
    ): Map<String, Map<String, String>> {
        return TaskOptionSupport.collectInputValidationErrors(
            options = options,
            selectedByOption = selectedByOption,
            inputValuesByOption = inputValuesByOption,
        )
    }

    fun defaultSelectionForOption(option: TaskOptionDescriptor): Set<String> {
        return TaskOptionSupport.defaultSelectionForOption(option)
    }
}

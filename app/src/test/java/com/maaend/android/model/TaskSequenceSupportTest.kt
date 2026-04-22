package com.maaend.android.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskSequenceSupportTest {
    @Test
    fun `ensureOpenGameFirst prepends android open game for task ids`() {
        assertEquals(
            listOf("AndroidOpenGame", "DijiangRewards", "VisitFriends"),
            TaskSequenceSupport.ensureOpenGameFirst(
                listOf("DijiangRewards", "VisitFriends"),
            ),
        )
    }

    @Test
    fun `ensureOpenGameFirst moves android open game to front for task ids`() {
        assertEquals(
            listOf("AndroidOpenGame", "DijiangRewards", "VisitFriends"),
            TaskSequenceSupport.ensureOpenGameFirst(
                listOf("DijiangRewards", "AndroidOpenGame", "VisitFriends"),
            ),
        )
    }

    @Test
    fun `ensureOpenGameFirst prepends android open game task descriptor when available`() {
        val openGame = task("AndroidOpenGame")
        val dijiang = task("DijiangRewards")
        val visitFriends = task("VisitFriends")

        assertEquals(
            listOf(openGame, dijiang, visitFriends),
            TaskSequenceSupport.ensureOpenGameFirst(
                tasks = listOf(dijiang, visitFriends),
                availableTasks = listOf(openGame, dijiang, visitFriends),
            ),
        )
    }

    @Test
    fun `ensureOpenGameFirst keeps original tasks when android open game is unavailable`() {
        val dijiang = task("DijiangRewards")
        val visitFriends = task("VisitFriends")

        assertEquals(
            listOf(dijiang, visitFriends),
            TaskSequenceSupport.ensureOpenGameFirst(
                tasks = listOf(dijiang, visitFriends),
                availableTasks = listOf(dijiang, visitFriends),
            ),
        )
    }

    private fun task(id: String) = TaskDescriptor(
        id = id,
        label = id,
        description = "",
        entry = id,
        groups = emptyList(),
        controllers = emptyList(),
        tier = TaskTier.MVP,
    )
}

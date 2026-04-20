package com.maaend.android.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeLogFilteringTest {
    @Test
    fun `info level hides uncategorized detail logs`() {
        val lines = listOf(
            "1710000000000 Loaded MaaFramework 1.2.3",
            "1710000000100 Run started: DailyRewards",
            "1710000000200 Warning: slow response",
            "1710000000300 Run completed",
        )

        assertEquals(
            listOf(
                "Run started: DailyRewards",
                "Warning: slow response",
                "Run completed",
            ),
            visibleRuntimeLogContents(lines, "info"),
        )
        assertEquals(3, visibleRuntimeLogCount(lines, "info"))
    }

    @Test
    fun `warn level keeps warnings and errors only`() {
        val lines = listOf(
            "1710000000000 Running task: DailyRewards",
            "1710000000100 timeout waiting for controller response",
            "1710000000200 Failed to bind tasker",
        )

        assertEquals(
            listOf(
                "timeout waiting for controller response",
                "Failed to bind tasker",
            ),
            visibleRuntimeLogContents(lines, "warn"),
        )
    }

    @Test
    fun `debug shows all logs and unknown level falls back to info`() {
        val lines = listOf(
            "1710000000000 Loaded MaaFramework 1.2.3",
            "1710000000100 Running task: DailyRewards",
        )

        assertEquals(
            listOf(
                "Loaded MaaFramework 1.2.3",
                "Running task: DailyRewards",
            ),
            visibleRuntimeLogContents(lines, "debug"),
        )
        assertEquals(
            listOf("Running task: DailyRewards"),
            visibleRuntimeLogContents(lines, "verbose"),
        )
    }

    @Test
    fun `malformed raw line without spaces does not crash filtering`() {
        val lines = listOf(
            "}",
            "1710000000100 Running task: DailyRewards",
        )

        assertEquals(
            listOf("Running task: DailyRewards"),
            visibleRuntimeLogContents(lines, "info"),
        )
        assertEquals(
            listOf(
                "}",
                "Running task: DailyRewards",
            ),
            visibleRuntimeLogContents(lines, "debug"),
        )
    }
}

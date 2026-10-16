package io.github.chronotask.sample.android

import io.github.chronotask.model.TaskState
import io.github.chronotask.sample.android.model.SampleTaskUiState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SampleTaskUiStateTest {

    @Test
    fun `test ui state data holder properties`() {
        val state = SampleTaskUiState(
            id = "test-task",
            bindingType = "KSP",
            scheduleDescription = "FixedRate 2s",
            state = TaskState.SCHEDULED,
            runCount = 10,
            successCount = 9,
            failureCount = 1,
            skippedCount = 2,
            lastStartedAt = "12:00:00",
            lastCompletedAt = "12:00:01",
            concurrencyPolicy = "SKIP_IF_RUNNING"
        )

        assertEquals("test-task", state.id)
        assertEquals("KSP", state.bindingType)
        assertEquals(TaskState.SCHEDULED, state.state)
        assertEquals(10, state.runCount)
        assertEquals(9, state.successCount)
        assertEquals(1, state.failureCount)
        assertEquals(2, state.skippedCount)
        assertEquals("SKIP_IF_RUNNING", state.concurrencyPolicy)
    }
}

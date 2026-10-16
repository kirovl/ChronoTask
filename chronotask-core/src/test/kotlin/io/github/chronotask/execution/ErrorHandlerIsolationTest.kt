package io.github.chronotask.execution

import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.trigger.ScheduledTrigger
import io.github.chronotask.trigger.TriggerSource
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class ErrorHandlerIsolationTest {

    private val sampleTrigger = ScheduledTrigger(
        scheduledAt = Instant.parse("2026-01-01T10:00:00Z"),
        discoveredAt = Instant.parse("2026-01-01T10:00:00Z"),
        source = TriggerSource.SCHEDULE
    )

    @Test
    fun `exception in error handler does not mask original task exception`() = runBlocking {
        val originalError = RuntimeException("Original Task Error")
        val definition = TaskDefinition(
            id = "task-isolation",
            name = "Isolation Task",
            schedule = TaskSchedule.FixedRate(1000L),
            executable = { throw originalError }
        )

        val execution = TaskExecution(
            taskId = "task-isolation",
            trigger = sampleTrigger
        )

        val failingErrorHandler = TaskErrorHandler { _, _, _ ->
            throw IllegalStateException("ErrorHandler threw an error!")
        }

        val result = DefaultTaskRunner.run(
            definition = definition,
            execution = execution,
            errorHandler = failingErrorHandler
        )

        assertTrue(result is ExecutionResult.Failed)
        val failed = result as ExecutionResult.Failed
        // Must preserve the original error, not the handler error
        assertSame(originalError, failed.error)
    }
}

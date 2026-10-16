package io.github.chronotask.execution

import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.trigger.ScheduledTrigger
import io.github.chronotask.trigger.TriggerSource
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.time.Instant

class TaskRunnerFailureTest {

    private val sampleTrigger = ScheduledTrigger(
        scheduledAt = Instant.parse("2026-01-01T10:00:00Z"),
        discoveredAt = Instant.parse("2026-01-01T10:00:00Z"),
        source = TriggerSource.SCHEDULE
    )

    @Test
    fun `task exception returns Failed and invokes ErrorHandler and Listener`() = runBlocking {
        val expectedException = IllegalStateException("Something went wrong")
        val definition = TaskDefinition(
            id = "task-fail",
            name = "Failing Task",
            schedule = TaskSchedule.FixedRate(1000L),
            executable = { throw expectedException }
        )

        val execution = TaskExecution(
            taskId = "task-fail",
            trigger = sampleTrigger
        )

        var handledError: Exception? = null
        val errorHandler = TaskErrorHandler { def, exec, err ->
            handledError = err
        }

        var listenerReportedError: Throwable? = null
        val events = mutableListOf<String>()
        val listener = object : ExecutionListener {
            override fun onStarted(def: TaskDefinition, exec: TaskExecution) {
                events.add("STARTED")
            }

            override fun onFailure(def: TaskDefinition, exec: TaskExecution, error: Throwable) {
                events.add("FAILURE")
                listenerReportedError = error
            }
        }

        val result = DefaultTaskRunner.run(
            definition = definition,
            execution = execution,
            errorHandler = errorHandler,
            listener = listener
        )

        assertInstanceOf(ExecutionResult.Failed::class.java, result)
        val failed = result as ExecutionResult.Failed
        assertSame(expectedException, failed.error)
        assertSame(expectedException, handledError)
        assertSame(expectedException, listenerReportedError)
        assertEquals(listOf("STARTED", "FAILURE"), events)
    }
}

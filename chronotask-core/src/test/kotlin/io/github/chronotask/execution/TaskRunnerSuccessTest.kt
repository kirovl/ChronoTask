package io.github.chronotask.execution

import io.github.chronotask.job.TaskExecutionContext
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.trigger.ScheduledTrigger
import io.github.chronotask.trigger.TriggerSource
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

class TaskRunnerSuccessTest {

    private val sampleTrigger = ScheduledTrigger(
        scheduledAt = Instant.parse("2026-01-01T10:00:00Z"),
        discoveredAt = Instant.parse("2026-01-01T10:00:00Z"),
        source = TriggerSource.SCHEDULE
    )

    @Test
    fun `successful execution maps context and returns Success`() = runBlocking {
        var observedContext: TaskExecutionContext? = null
        val definition = TaskDefinition(
            id = "task-success",
            name = "Success Task",
            schedule = TaskSchedule.FixedRate(1000L),
            executable = { ctx ->
                observedContext = ctx
            }
        )

        val execution = TaskExecution(
            taskId = "task-success",
            trigger = sampleTrigger,
            attempt = 2
        )

        val events = mutableListOf<String>()
        val listener = object : ExecutionListener {
            override fun onStarted(def: TaskDefinition, exec: TaskExecution) {
                events.add("STARTED")
            }

            override fun onSuccess(def: TaskDefinition, exec: TaskExecution) {
                events.add("SUCCESS")
            }
        }

        val result = DefaultTaskRunner.run(
            definition = definition,
            execution = execution,
            listener = listener
        )

        assertEquals(ExecutionResult.Success, result)
        assertEquals(listOf("STARTED", "SUCCESS"), events)
        assertEquals("task-success", observedContext?.taskId)
        assertEquals(sampleTrigger, observedContext?.trigger)
        assertEquals(2, observedContext?.attempt)
    }
}

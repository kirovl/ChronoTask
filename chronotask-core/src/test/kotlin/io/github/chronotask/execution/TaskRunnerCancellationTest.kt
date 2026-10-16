package io.github.chronotask.execution

import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.trigger.ScheduledTrigger
import io.github.chronotask.trigger.TriggerSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class TaskRunnerCancellationTest {

    private val sampleTrigger = ScheduledTrigger(
        scheduledAt = Instant.parse("2026-01-01T10:00:00Z"),
        discoveredAt = Instant.parse("2026-01-01T10:00:00Z"),
        source = TriggerSource.SCHEDULE
    )

    @Test
    fun `coroutine cancellation calls onCancelled and rethrows CancellationException`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val cancelledCalled = CompletableDeferred<Unit>()

        val definition = TaskDefinition(
            id = "task-cancel",
            name = "Cancellable Task",
            schedule = TaskSchedule.FixedRate(1000L),
            executable = {
                started.complete(Unit)
                kotlinx.coroutines.awaitCancellation()
            }
        )

        val execution = TaskExecution(
            taskId = "task-cancel",
            trigger = sampleTrigger
        )

        val events = mutableListOf<String>()
        val listener = object : ExecutionListener {
            override fun onStarted(def: TaskDefinition, exec: TaskExecution) {
                events.add("STARTED")
            }

            override fun onCancelled(def: TaskDefinition, exec: TaskExecution) {
                events.add("CANCELLED")
                cancelledCalled.complete(Unit)
            }
        }

        var exceptionThrown: Throwable? = null
        val job = launch {
            try {
                DefaultTaskRunner.run(
                    definition = definition,
                    execution = execution,
                    listener = listener
                )
            } catch (t: Throwable) {
                exceptionThrown = t
            }
        }

        started.await()
        job.cancel()
        job.join()

        cancelledCalled.await()
        assertTrue(exceptionThrown is CancellationException, "CancellationException must be rethrown")
        assertEquals(listOf("STARTED", "CANCELLED"), events)
    }
}

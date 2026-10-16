package io.github.chronotask.concurrency

import io.github.chronotask.execution.SubmissionResult
import io.github.chronotask.execution.TaskExecution
import io.github.chronotask.model.ConcurrencyPolicy
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.trigger.ScheduledTrigger
import io.github.chronotask.trigger.TriggerSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

class AllowConcurrencyTest {

    private val sampleTrigger = ScheduledTrigger(
        scheduledAt = Instant.parse("2026-01-01T10:00:00Z"),
        discoveredAt = Instant.parse("2026-01-01T10:00:00Z"),
        source = TriggerSource.SCHEDULE
    )

    @Test
    fun `Allow policy allows multiple simultaneous executions`() = runBlocking {
        val controller = DefaultConcurrencyController(dispatcher = Dispatchers.Default)
        val startedCount = AtomicInteger(0)
        val releaseBarrier = CompletableDeferred<Unit>()

        val definition = TaskDefinition(
            id = "allow-task",
            name = "Allow Task",
            schedule = TaskSchedule.FixedRate(1000L),
            concurrencyPolicy = ConcurrencyPolicy.ALLOW,
            executable = {
                startedCount.incrementAndGet()
                releaseBarrier.await()
            }
        )

        val exec1 = TaskExecution(taskId = "allow-task", trigger = sampleTrigger)
        val exec2 = TaskExecution(taskId = "allow-task", trigger = sampleTrigger)
        val exec3 = TaskExecution(taskId = "allow-task", trigger = sampleTrigger)

        val res1 = controller.submit(definition, exec1)
        val res2 = controller.submit(definition, exec2)
        val res3 = controller.submit(definition, exec3)

        assertTrue(res1 is SubmissionResult.Started)
        assertTrue(res2 is SubmissionResult.Started)
        assertTrue(res3 is SubmissionResult.Started)

        // Wait until all 3 start concurrently
        while (startedCount.get() < 3) {
            kotlinx.coroutines.delay(10)
        }

        assertEquals(3, controller.activeCount("allow-task"))

        // Release all
        releaseBarrier.complete(Unit)
        controller.close()
    }
}

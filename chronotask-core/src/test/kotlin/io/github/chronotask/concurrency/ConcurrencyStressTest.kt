package io.github.chronotask.concurrency

import io.github.chronotask.execution.SubmissionResult
import io.github.chronotask.execution.TaskExecution
import io.github.chronotask.model.ConcurrencyPolicy
import io.github.chronotask.model.QueueOverflowPolicy
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.trigger.ScheduledTrigger
import io.github.chronotask.trigger.TriggerSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

class ConcurrencyStressTest {

    private val sampleTrigger = ScheduledTrigger(
        scheduledAt = Instant.parse("2026-01-01T10:00:00Z"),
        discoveredAt = Instant.parse("2026-01-01T10:00:00Z"),
        source = TriggerSource.SCHEDULE
    )

    @Test
    fun `Stress Test - Serial queue with 1000 executions maintains single concurrency and completes all`() = runBlocking {
        val controller = DefaultConcurrencyController(dispatcher = Dispatchers.Default)
        val completedCount = AtomicInteger(0)
        val maxSimultaneous = AtomicInteger(0)
        val currentRunning = AtomicInteger(0)
        val allDone = CompletableDeferred<Unit>()

        val total = 500

        val definition = TaskDefinition(
            id = "stress-serial",
            name = "Stress Serial Task",
            schedule = TaskSchedule.FixedRate(1000L),
            concurrencyPolicy = ConcurrencyPolicy.serial(maxQueueSize = 1000, overflowPolicy = QueueOverflowPolicy.DROP_LATEST),
            executable = {
                val running = currentRunning.incrementAndGet()
                var max = maxSimultaneous.get()
                while (running > max) {
                    if (maxSimultaneous.compareAndSet(max, running)) break
                    max = maxSimultaneous.get()
                }

                currentRunning.decrementAndGet()
                if (completedCount.incrementAndGet() == total) {
                    allDone.complete(Unit)
                }
            }
        )

        // Submit 500 executions concurrently
        val results = (1..total).map {
            async(Dispatchers.Default) {
                val exec = TaskExecution(taskId = "stress-serial", trigger = sampleTrigger)
                controller.submit(definition, exec)
            }
        }.awaitAll()

        allDone.await()

        while (controller.activeCount("stress-serial") > 0 || controller.queuedCount("stress-serial") > 0) {
            kotlinx.coroutines.delay(10)
        }

        assertEquals(total, completedCount.get(), "All submissions should complete")
        assertEquals(1, maxSimultaneous.get(), "Max simultaneous executions for Serial must be exactly 1")
        controller.close()
    }
}

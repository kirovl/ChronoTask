package io.github.chronotask.concurrency

import io.github.chronotask.execution.SkipReason
import io.github.chronotask.execution.SubmissionResult
import io.github.chronotask.execution.TaskExecution
import io.github.chronotask.model.ConcurrencyPolicy
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

class SkipIfRunningTest {

    private val sampleTrigger = ScheduledTrigger(
        scheduledAt = Instant.parse("2026-01-01T10:00:00Z"),
        discoveredAt = Instant.parse("2026-01-01T10:00:00Z"),
        source = TriggerSource.SCHEDULE
    )

    @Test
    fun `SkipIfRunning starts first and skips subsequent while running`() = runBlocking {
        val controller = DefaultConcurrencyController(dispatcher = Dispatchers.Default)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val definition = TaskDefinition(
            id = "skip-task",
            name = "Skip Task",
            schedule = TaskSchedule.FixedRate(1000L),
            concurrencyPolicy = ConcurrencyPolicy.SKIP_IF_RUNNING,
            executable = {
                started.complete(Unit)
                release.await()
            }
        )

        val exec1 = TaskExecution(taskId = "skip-task", trigger = sampleTrigger)
        val exec2 = TaskExecution(taskId = "skip-task", trigger = sampleTrigger)

        val res1 = controller.submit(definition, exec1)
        assertTrue(res1 is SubmissionResult.Started)

        started.await()

        val res2 = controller.submit(definition, exec2)
        assertTrue(res2 is SubmissionResult.Skipped)
        assertEquals(SkipReason.ALREADY_RUNNING, (res2 as SubmissionResult.Skipped).reason)

        release.complete(Unit)
        controller.close()
    }

    @Test
    fun `1000 concurrent submissions with SkipIfRunning yields exactly 1 Started and 999 Skipped`() = runBlocking {
        val controller = DefaultConcurrencyController(dispatcher = Dispatchers.Default)
        val startedSignal = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val executionCounter = AtomicInteger(0)

        val definition = TaskDefinition(
            id = "stress-skip",
            name = "Stress Skip Task",
            schedule = TaskSchedule.FixedRate(1000L),
            concurrencyPolicy = ConcurrencyPolicy.SKIP_IF_RUNNING,
            executable = {
                executionCounter.incrementAndGet()
                startedSignal.complete(Unit)
                release.await()
            }
        )

        val submissions = (1..1000).map {
            async(Dispatchers.Default) {
                val exec = TaskExecution(taskId = "stress-skip", trigger = sampleTrigger)
                controller.submit(definition, exec)
            }
        }.awaitAll()

        startedSignal.await()

        val startedCount = submissions.count { it is SubmissionResult.Started }
        val skippedCount = submissions.count { it is SubmissionResult.Skipped && it.reason == SkipReason.ALREADY_RUNNING }

        assertEquals(1, startedCount, "Exactly 1 execution must be Started")
        assertEquals(999, skippedCount, "Remaining 999 executions must be Skipped")
        assertEquals(1, executionCounter.get(), "TaskExecutable must execute exactly once")

        release.complete(Unit)
        controller.close()
    }
}

package io.github.chronotask.concurrency

import io.github.chronotask.execution.ExecutionListener
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
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

class QueueOneTest {

    private val sampleTrigger = ScheduledTrigger(
        scheduledAt = Instant.parse("2026-01-01T10:00:00Z"),
        discoveredAt = Instant.parse("2026-01-01T10:00:00Z"),
        source = TriggerSource.SCHEDULE
    )

    @Test
    fun `QueueOne coalesces multiple pending submissions keeping latest-wins`() = runBlocking {
        val executedList = CopyOnWriteArrayList<String>()
        val skippedList = CopyOnWriteArrayList<Pair<String, SkipReason>>()

        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondDone = CompletableDeferred<Unit>()

        val listener = object : ExecutionListener {
            override fun onSkipped(def: TaskDefinition, exec: TaskExecution, reason: SkipReason) {
                skippedList.add(exec.taskId to reason)
            }
        }

        val controller = DefaultConcurrencyController(
            dispatcher = Dispatchers.Default,
            listener = listener
        )

        val definition = TaskDefinition(
            id = "queue-one-task",
            name = "QueueOne Task",
            schedule = TaskSchedule.FixedRate(1000L),
            concurrencyPolicy = ConcurrencyPolicy.QUEUE_ONE,
            executable = { ctx ->
                executedList.add(ctx.taskId)
                if (executedList.size == 1) {
                    firstStarted.complete(Unit)
                    releaseFirst.await()
                } else {
                    secondDone.complete(Unit)
                }
            }
        )

        val execA = TaskExecution(taskId = "exec-A", trigger = sampleTrigger)
        val execB = TaskExecution(taskId = "exec-B", trigger = sampleTrigger)
        val execC = TaskExecution(taskId = "exec-C", trigger = sampleTrigger)
        val execD = TaskExecution(taskId = "exec-D", trigger = sampleTrigger)

        val resA = controller.submit(definition, execA)
        assertTrue(resA is SubmissionResult.Started)

        firstStarted.await()

        val resB = controller.submit(definition, execB)
        assertTrue(resB is SubmissionResult.Queued)

        val resC = controller.submit(definition, execC)
        assertTrue(resC is SubmissionResult.Queued)

        val resD = controller.submit(definition, execD)
        assertTrue(resD is SubmissionResult.Queued)

        assertEquals(1, controller.queuedCount("queue-one-task"))

        // Release first execution
        releaseFirst.complete(Unit)
        secondDone.await()

        // Wait for queue to drain
        while (controller.activeCount("queue-one-task") > 0 || controller.queuedCount("queue-one-task") > 0) {
            kotlinx.coroutines.delay(10)
        }

        // Only A and D should execute! B and C must be skipped as QUEUE_REPLACED
        assertEquals(listOf("exec-A", "exec-D"), executedList)
        assertEquals(2, skippedList.size)
        assertEquals(listOf("exec-B", "exec-C"), skippedList.map { it.first })
        assertEquals(listOf(SkipReason.QUEUE_REPLACED, SkipReason.QUEUE_REPLACED), skippedList.map { it.second })

        controller.close()
    }
}

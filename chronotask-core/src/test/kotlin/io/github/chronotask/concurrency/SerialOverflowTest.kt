package io.github.chronotask.concurrency

import io.github.chronotask.execution.ExecutionListener
import io.github.chronotask.execution.RejectionReason
import io.github.chronotask.execution.SkipReason
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
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

class SerialOverflowTest {

    private val sampleTrigger = ScheduledTrigger(
        scheduledAt = Instant.parse("2026-01-01T10:00:00Z"),
        discoveredAt = Instant.parse("2026-01-01T10:00:00Z"),
        source = TriggerSource.SCHEDULE
    )

    @Test
    fun `Serial DROP_OLDEST replaces oldest queued item`() = runBlocking {
        val skippedList = CopyOnWriteArrayList<Pair<String, SkipReason>>()
        val firstStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val listener = object : ExecutionListener {
            override fun onSkipped(def: TaskDefinition, exec: TaskExecution, reason: SkipReason) {
                skippedList.add(exec.taskId to reason)
            }
        }

        val controller = DefaultConcurrencyController(
            dispatcher = Dispatchers.Default,
            listener = listener
        )

        // maxQueueSize = 2
        val definition = TaskDefinition(
            id = "drop-oldest-task",
            name = "Drop Oldest Task",
            schedule = TaskSchedule.FixedRate(1000L),
            concurrencyPolicy = ConcurrencyPolicy.serial(maxQueueSize = 2, overflowPolicy = QueueOverflowPolicy.DROP_OLDEST),
            executable = {
                firstStarted.complete(Unit)
                release.await()
            }
        )

        val execA = TaskExecution(taskId = "A", trigger = sampleTrigger)
        val execB = TaskExecution(taskId = "B", trigger = sampleTrigger)
        val execC = TaskExecution(taskId = "C", trigger = sampleTrigger)
        val execD = TaskExecution(taskId = "D", trigger = sampleTrigger)

        controller.submit(definition, execA)
        firstStarted.await()

        controller.submit(definition, execB)
        controller.submit(definition, execC)
        // Queue full: [B, C]. Submitting D should drop B!
        val resD = controller.submit(definition, execD)

        assertTrue(resD is SubmissionResult.Queued)
        assertEquals(2, controller.queuedCount("drop-oldest-task"))
        assertEquals(listOf("B" to SkipReason.QUEUE_REPLACED), skippedList)

        release.complete(Unit)
        controller.close()
    }

    @Test
    fun `Serial DROP_LATEST skips incoming item when queue is full`() = runBlocking {
        val skippedList = CopyOnWriteArrayList<Pair<String, SkipReason>>()
        val firstStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

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
            id = "drop-latest-task",
            name = "Drop Latest Task",
            schedule = TaskSchedule.FixedRate(1000L),
            concurrencyPolicy = ConcurrencyPolicy.serial(maxQueueSize = 2, overflowPolicy = QueueOverflowPolicy.DROP_LATEST),
            executable = {
                firstStarted.complete(Unit)
                release.await()
            }
        )

        val execA = TaskExecution(taskId = "A", trigger = sampleTrigger)
        val execB = TaskExecution(taskId = "B", trigger = sampleTrigger)
        val execC = TaskExecution(taskId = "C", trigger = sampleTrigger)
        val execD = TaskExecution(taskId = "D", trigger = sampleTrigger)

        controller.submit(definition, execA)
        firstStarted.await()

        controller.submit(definition, execB)
        controller.submit(definition, execC)
        // Queue full: [B, C]. Submitting D should skip D!
        val resD = controller.submit(definition, execD)

        assertTrue(resD is SubmissionResult.Skipped)
        assertEquals(SkipReason.QUEUE_FULL, (resD as SubmissionResult.Skipped).reason)
        assertEquals(listOf("D" to SkipReason.QUEUE_FULL), skippedList)

        release.complete(Unit)
        controller.close()
    }

    @Test
    fun `Serial REJECT returns Rejected result when queue is full`() = runBlocking {
        val firstStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val controller = DefaultConcurrencyController(dispatcher = Dispatchers.Default)

        val definition = TaskDefinition(
            id = "reject-task",
            name = "Reject Task",
            schedule = TaskSchedule.FixedRate(1000L),
            concurrencyPolicy = ConcurrencyPolicy.serial(maxQueueSize = 1, overflowPolicy = QueueOverflowPolicy.REJECT),
            executable = {
                firstStarted.complete(Unit)
                release.await()
            }
        )

        val execA = TaskExecution(taskId = "A", trigger = sampleTrigger)
        val execB = TaskExecution(taskId = "B", trigger = sampleTrigger)
        val execC = TaskExecution(taskId = "C", trigger = sampleTrigger)

        controller.submit(definition, execA)
        firstStarted.await()

        val resB = controller.submit(definition, execB)
        assertTrue(resB is SubmissionResult.Queued)

        // Queue full: [B]. Submitting C should return Rejected!
        val resC = controller.submit(definition, execC)
        assertTrue(resC is SubmissionResult.Rejected)
        assertEquals(RejectionReason.QUEUE_FULL, (resC as SubmissionResult.Rejected).reason)

        release.complete(Unit)
        controller.close()
    }
}

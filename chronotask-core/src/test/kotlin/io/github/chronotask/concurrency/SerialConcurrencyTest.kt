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
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

class SerialConcurrencyTest {

    private val sampleTrigger = ScheduledTrigger(
        scheduledAt = Instant.parse("2026-01-01T10:00:00Z"),
        discoveredAt = Instant.parse("2026-01-01T10:00:00Z"),
        source = TriggerSource.SCHEDULE
    )

    @Test
    fun `Serial policy executes queued items sequentially in FIFO order`() = runBlocking {
        val executionOrder = CopyOnWriteArrayList<String>()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val allDone = CompletableDeferred<Unit>()

        val controller = DefaultConcurrencyController(dispatcher = Dispatchers.Default)

        val definition = TaskDefinition(
            id = "serial-task",
            name = "Serial Task",
            schedule = TaskSchedule.FixedRate(1000L),
            concurrencyPolicy = ConcurrencyPolicy.serial(maxQueueSize = 10, overflowPolicy = QueueOverflowPolicy.DROP_LATEST),
            executable = { ctx ->
                executionOrder.add(ctx.taskId)
                if (executionOrder.size == 1) {
                    firstStarted.complete(Unit)
                    releaseFirst.await()
                }
                if (executionOrder.size == 3) {
                    allDone.complete(Unit)
                }
            }
        )

        val exec1 = TaskExecution(taskId = "task-item-1", trigger = sampleTrigger)
        val exec2 = TaskExecution(taskId = "task-item-2", trigger = sampleTrigger)
        val exec3 = TaskExecution(taskId = "task-item-3", trigger = sampleTrigger)

        val res1 = controller.submit(definition, exec1)
        assertTrue(res1 is SubmissionResult.Started)

        firstStarted.await()

        val res2 = controller.submit(definition, exec2)
        val res3 = controller.submit(definition, exec3)

        assertTrue(res2 is SubmissionResult.Queued)
        assertTrue(res3 is SubmissionResult.Queued)
        assertEquals(2, controller.queuedCount("serial-task"))

        releaseFirst.complete(Unit)
        allDone.await()

        while (controller.activeCount("serial-task") > 0 || controller.queuedCount("serial-task") > 0) {
            kotlinx.coroutines.delay(10)
        }

        assertEquals(listOf("task-item-1", "task-item-2", "task-item-3"), executionOrder)
        controller.close()
    }

    @Test
    fun `Serial queue continues execution even if earlier execution throws exception`() = runBlocking {
        val executionOrder = CopyOnWriteArrayList<String>()
        val allDone = CompletableDeferred<Unit>()

        val controller = DefaultConcurrencyController(dispatcher = Dispatchers.Default)

        val definition = TaskDefinition(
            id = "serial-fail-task",
            name = "Serial Fail Task",
            schedule = TaskSchedule.FixedRate(1000L),
            concurrencyPolicy = ConcurrencyPolicy.serial(maxQueueSize = 5),
            executable = { ctx ->
                executionOrder.add(ctx.taskId)
                if (ctx.taskId == "failing-item") {
                    throw IllegalStateException("Intentional Failure")
                }
                if (executionOrder.size == 2) {
                    allDone.complete(Unit)
                }
            }
        )

        val exec1 = TaskExecution(taskId = "failing-item", trigger = sampleTrigger)
        val exec2 = TaskExecution(taskId = "succeeding-item", trigger = sampleTrigger)

        controller.submit(definition, exec1)
        controller.submit(definition, exec2)

        allDone.await()

        assertEquals(listOf("failing-item", "succeeding-item"), executionOrder)
        controller.close()
    }
}

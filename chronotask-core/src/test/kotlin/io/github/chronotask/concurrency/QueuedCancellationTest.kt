package io.github.chronotask.concurrency

import io.github.chronotask.execution.ExecutionListener
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

class QueuedCancellationTest {

    private val sampleTrigger = ScheduledTrigger(
        scheduledAt = Instant.parse("2026-01-01T10:00:00Z"),
        discoveredAt = Instant.parse("2026-01-01T10:00:00Z"),
        source = TriggerSource.SCHEDULE
    )

    @Test
    fun `cancelling queued execution removes it without ghost execution`() = runBlocking {
        val executedList = CopyOnWriteArrayList<String>()
        val cancelledList = CopyOnWriteArrayList<String>()
        val firstStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val thirdDone = CompletableDeferred<Unit>()

        val listener = object : ExecutionListener {
            override fun onCancelled(def: TaskDefinition, exec: TaskExecution) {
                cancelledList.add(exec.taskId)
            }
        }

        val controller = DefaultConcurrencyController(
            dispatcher = Dispatchers.Default,
            listener = listener
        )

        val definition = TaskDefinition(
            id = "cancel-task",
            name = "Cancel Task",
            schedule = TaskSchedule.FixedRate(1000L),
            concurrencyPolicy = ConcurrencyPolicy.serial(maxQueueSize = 5),
            executable = { ctx ->
                executedList.add(ctx.taskId)
                if (ctx.taskId == "A") {
                    firstStarted.complete(Unit)
                    release.await()
                } else if (ctx.taskId == "C") {
                    thirdDone.complete(Unit)
                }
            }
        )

        val execA = TaskExecution(taskId = "A", trigger = sampleTrigger)
        val execB = TaskExecution(taskId = "B", trigger = sampleTrigger)
        val execC = TaskExecution(taskId = "C", trigger = sampleTrigger)

        controller.submit(definition, execA)
        firstStarted.await()

        controller.submit(definition, execB)
        controller.submit(definition, execC)

        assertEquals(2, controller.queuedCount("cancel-task"))

        // Cancel B while in queue
        val cancelled = controller.cancelExecution("cancel-task", execB.executionId)
        assertTrue(cancelled)
        assertEquals(1, controller.queuedCount("cancel-task"))
        assertEquals(listOf("B"), cancelledList)

        // Release A
        release.complete(Unit)
        thirdDone.await()

        while (controller.activeCount("cancel-task") > 0 || controller.queuedCount("cancel-task") > 0) {
            kotlinx.coroutines.delay(10)
        }

        // B must NOT have been executed! Only A and C.
        assertEquals(listOf("A", "C"), executedList)
        controller.close()
    }
}

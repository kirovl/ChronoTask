package io.github.chronotask.concurrency

import io.github.chronotask.execution.ExecutionListener
import io.github.chronotask.execution.RejectionReason
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

class ControllerCloseTest {

    private val sampleTrigger = ScheduledTrigger(
        scheduledAt = Instant.parse("2026-01-01T10:00:00Z"),
        discoveredAt = Instant.parse("2026-01-01T10:00:00Z"),
        source = TriggerSource.SCHEDULE
    )

    @Test
    fun `Controller close rejects new submissions and cancels active and queued tasks`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val cancelledList = CopyOnWriteArrayList<String>()

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
            id = "close-task",
            name = "Close Task",
            schedule = TaskSchedule.FixedRate(1000L),
            concurrencyPolicy = ConcurrencyPolicy.serial(maxQueueSize = 5),
            executable = {
                started.complete(Unit)
                kotlinx.coroutines.awaitCancellation()
            }
        )

        val execA = TaskExecution(taskId = "A", trigger = sampleTrigger)
        val execB = TaskExecution(taskId = "B", trigger = sampleTrigger)

        controller.submit(definition, execA)
        started.await()
        controller.submit(definition, execB)

        // Close controller
        controller.close()

        val execC = TaskExecution(taskId = "C", trigger = sampleTrigger)
        val resC = controller.submit(definition, execC)

        assertTrue(resC is SubmissionResult.Rejected)
        assertEquals(RejectionReason.CONTROLLER_CLOSED, (resC as SubmissionResult.Rejected).reason)

        // Wait until cancellation processes
        while (controller.activeCount("close-task") > 0 || controller.queuedCount("close-task") > 0) {
            kotlinx.coroutines.delay(10)
        }

        assertTrue(cancelledList.contains("A"), "Running task A should be cancelled")
        assertTrue(cancelledList.contains("B"), "Queued task B should be cancelled")
    }
}

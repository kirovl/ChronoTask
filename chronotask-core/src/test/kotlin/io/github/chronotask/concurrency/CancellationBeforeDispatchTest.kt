package io.github.chronotask.concurrency

import io.github.chronotask.execution.ExecutionListener
import io.github.chronotask.execution.SubmissionResult
import io.github.chronotask.execution.TaskExecution
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.trigger.ScheduledTrigger
import io.github.chronotask.trigger.TriggerSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CancellationBeforeDispatchTest {
    @Test
    fun `cancelling before dispatch releases the running slot and emits one cancellation`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val cancelled = mutableListOf<TaskExecution>()
        val controller = DefaultConcurrencyController(
            coroutineScope = scope,
            listener = object : ExecutionListener {
                override fun onCancelled(definition: TaskDefinition, execution: TaskExecution) {
                    cancelled.add(execution)
                }
            }
        )
        var executions = 0
        val definition = TaskDefinition("task", schedule = TaskSchedule.Once(), executable = { executions++ })
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val trigger = ScheduledTrigger(now, now, TriggerSource.MANUAL)
        val first = TaskExecution(taskId = "task", trigger = trigger)
        val second = TaskExecution(taskId = "task", trigger = trigger)
        try {
            assertTrue(controller.submit(definition, first) is SubmissionResult.Started)
            assertTrue(controller.cancelExecution("task", first.executionId))
            runCurrent()
            assertEquals(listOf(first), cancelled)
            assertEquals(0, controller.activeCount("task"))
            assertTrue(controller.submit(definition, second) is SubmissionResult.Started)
            runCurrent()
            assertEquals(1, executions)
            assertEquals(listOf(first), cancelled)
        } finally {
            controller.close()
            scope.cancel()
        }
    }
}

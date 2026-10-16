package io.github.chronotask.execution

import io.github.chronotask.concurrency.DefaultConcurrencyController
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.trigger.ScheduledTrigger
import io.github.chronotask.trigger.TriggerSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TaskErrorRecoveryTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val trigger = ScheduledTrigger(now, now, TriggerSource.MANUAL)

    @Test
    fun `the default runner still propagates Errors to its caller`() {
        val error = AssertionError("task error")
        val definition = TaskDefinition("task", schedule = TaskSchedule.Once(), executable = { throw error })
        val execution = TaskExecution(taskId = definition.id, trigger = trigger)
        val thrown = assertThrows(AssertionError::class.java) {
            runBlocking { DefaultTaskRunner.run(definition, execution) }
        }
        assertSame(error, thrown)
    }

    @Test
    fun `an Error after a runner failure notification does not duplicate the terminal event`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val error = AssertionError("runner error")
        val failures = mutableListOf<TaskExecution>()
        val controller = DefaultConcurrencyController(
            coroutineScope = scope,
            runner = object : TaskRunner {
                override suspend fun run(
                    definition: TaskDefinition,
                    execution: TaskExecution,
                    errorHandler: TaskErrorHandler?,
                    listener: ExecutionListener?
                ): ExecutionResult {
                    listener?.onStarted(definition, execution)
                    listener?.onFailure(definition, execution, error)
                    throw error
                }
            },
            listener = object : ExecutionListener {
                override fun onFailure(definition: TaskDefinition, execution: TaskExecution, error: Throwable) {
                    failures.add(execution)
                }
            }
        )
        val definition = TaskDefinition("task", schedule = TaskSchedule.Once(), executable = {})
        val first = TaskExecution(taskId = definition.id, trigger = trigger)
        val second = TaskExecution(taskId = definition.id, trigger = trigger)
        try {
            assertTrue(controller.submit(definition, first) is SubmissionResult.Started)
            runCurrent()
            assertEquals(listOf(first), failures)
            assertEquals(0, controller.activeCount(definition.id))
            assertTrue(controller.submit(definition, second) is SubmissionResult.Started)
            runCurrent()
            assertEquals(listOf(first, second), failures)
            assertEquals(0, controller.activeCount(definition.id))
        } finally {
            controller.close()
            scope.cancel()
        }
    }
}

package io.github.chronotask.reflect.invocation

import io.github.chronotask.annotation.Once
import io.github.chronotask.execution.DefaultTaskRunner
import io.github.chronotask.execution.ExecutionResult
import io.github.chronotask.execution.TaskExecution
import io.github.chronotask.job.TaskExecutionContext
import io.github.chronotask.reflect.ReflectionTaskScanner
import io.github.chronotask.trigger.ScheduledTrigger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.time.Instant

class PrivateClassSuspendInvocationTest {
    private class Job {
        var calls = 0
        var receivedTaskId: String? = null
        val error = IllegalStateException("task failure")

        @Once(id = "no-args")
        suspend fun run() { yield(); calls++ }

        @Once(id = "context")
        suspend fun runWithContext(context: TaskExecutionContext) {
            yield()
            receivedTaskId = context.taskId
        }

        @Once(id = "failure")
        suspend fun fail() { yield(); throw error }
    }

    private suspend fun execute(job: Job, taskId: String): ExecutionResult {
        val definition = ReflectionTaskScanner.scan(job).first { it.id == taskId }
        val now = Instant.parse("2026-01-01T00:00:00Z")
        return DefaultTaskRunner.run(definition, TaskExecution(taskId = taskId, trigger = ScheduledTrigger(now, now)))
    }

    @Test fun publicSuspendTaskOnPrivateClassIsInvocable() = runBlocking {
        val job = Job()
        assertEquals(ExecutionResult.Success, execute(job, "no-args"))
        assertEquals(1, job.calls)
    }

    @Test fun privateClassSuspendTaskReceivesExecutionContext() = runBlocking {
        val job = Job()
        assertEquals(ExecutionResult.Success, execute(job, "context"))
        assertEquals("context", job.receivedTaskId)
    }

    @Test fun privateClassSuspendTaskPreservesOriginalFailure() = runBlocking {
        val job = Job()
        val result = assertInstanceOf(ExecutionResult.Failed::class.java, execute(job, "failure"))
        assertSame(job.error, result.error)
    }
}

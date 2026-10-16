package io.github.chronotask.reflect.invocation

import io.github.chronotask.execution.DefaultTaskRunner
import io.github.chronotask.execution.ExecutionId
import io.github.chronotask.execution.ExecutionResult
import io.github.chronotask.execution.TaskExecution
import io.github.chronotask.job.TaskExecutionContext
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.reflect.ReflectionMethodAdapter
import io.github.chronotask.trigger.ScheduledTrigger
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant

class ReflectionContextInvocationTest {

    private val now = Instant.parse("2026-01-01T00:00:00Z")

    class ContextService {
        var receivedTaskId: String? = null
        var receivedTriggerTime: Instant? = null

        fun executeWithContext(ctx: TaskExecutionContext) {
            receivedTaskId = ctx.taskId
            receivedTriggerTime = ctx.trigger.scheduledAt
        }
    }

    @Test
    fun `executes normal method with TaskExecutionContext argument`() = runBlocking {
        val service = ContextService()
        val method = service.javaClass.getMethod("executeWithContext", TaskExecutionContext::class.java)
        val adapter = ReflectionMethodAdapter(service, method)

        val taskDef = TaskDefinition(
            id = "ctx-task",
            name = "Ctx Task",
            schedule = TaskSchedule.Once(0),
            executable = adapter
        )

        val triggerTime = Instant.parse("2026-06-01T12:00:00Z")
        val execution = TaskExecution(
            executionId = ExecutionId.generate(),
            taskId = "ctx-task",
            trigger = ScheduledTrigger(scheduledAt = triggerTime, discoveredAt = triggerTime)
        )

        val result = DefaultTaskRunner.run(taskDef, execution)

        assertInstanceOf(ExecutionResult.Success::class.java, result)
        assertEquals("ctx-task", service.receivedTaskId)
        assertEquals(triggerTime, service.receivedTriggerTime)
    }
}

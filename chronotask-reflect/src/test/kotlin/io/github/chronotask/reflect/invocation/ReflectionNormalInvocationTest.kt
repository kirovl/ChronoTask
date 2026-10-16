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

class ReflectionNormalInvocationTest {

    private val now = Instant.parse("2026-01-01T00:00:00Z")

    class NormalService {
        var callCount = 0

        fun executeWork() {
            callCount++
        }
    }

    @Test
    fun `executes normal no-arg Kotlin method successfully`() = runBlocking {
        val service = NormalService()
        val method = service.javaClass.getMethod("executeWork")
        val adapter = ReflectionMethodAdapter(service, method)

        val taskDef = TaskDefinition(
            id = "normal-task",
            name = "Normal Task",
            schedule = TaskSchedule.Once(0),
            executable = adapter
        )

        val execution = TaskExecution(
            executionId = ExecutionId.generate(),
            taskId = "normal-task",
            trigger = ScheduledTrigger(scheduledAt = now, discoveredAt = now)
        )

        val result = DefaultTaskRunner.run(taskDef, execution)

        assertInstanceOf(ExecutionResult.Success::class.java, result)
        assertEquals(1, service.callCount)
    }
}

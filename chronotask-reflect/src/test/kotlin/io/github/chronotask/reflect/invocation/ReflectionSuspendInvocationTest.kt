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
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class ReflectionSuspendInvocationTest {

    private val now = Instant.parse("2026-01-01T00:00:00Z")

    class SuspendService {
        var steps = mutableListOf<String>()

        suspend fun longRunningTask(ctx: TaskExecutionContext) {
            steps.add("start:${ctx.taskId}")
            delay(50)
            steps.add("step1")
            delay(50)
            steps.add("end")
        }
    }

    @Test
    fun `suspend function steps through multiple suspension points and records completion`() = runBlocking {
        val service = SuspendService()
        val method = service.javaClass.methods.first { it.name == "longRunningTask" }
        val adapter = ReflectionMethodAdapter(service, method)

        val taskDef = TaskDefinition(
            id = "multi-suspend-task",
            name = "Multi Suspend Task",
            schedule = TaskSchedule.Once(0),
            executable = adapter
        )

        val execution = TaskExecution(
            executionId = ExecutionId.generate(),
            taskId = "multi-suspend-task",
            trigger = ScheduledTrigger(scheduledAt = now, discoveredAt = now)
        )

        val result = DefaultTaskRunner.run(taskDef, execution)

        assertInstanceOf(ExecutionResult.Success::class.java, result)
        assertEquals(listOf("start:multi-suspend-task", "step1", "end"), service.steps)
    }
}

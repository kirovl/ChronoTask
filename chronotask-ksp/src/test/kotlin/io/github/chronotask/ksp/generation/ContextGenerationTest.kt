package io.github.chronotask.ksp.generation

import io.github.chronotask.annotation.Once
import io.github.chronotask.execution.DefaultTaskRunner
import io.github.chronotask.execution.ExecutionId
import io.github.chronotask.execution.ExecutionResult
import io.github.chronotask.execution.TaskExecution
import io.github.chronotask.job.TaskExecutionContext
import io.github.chronotask.trigger.ScheduledTrigger
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

class ContextTarget {
    val syncContextTaskId = AtomicReference<String>()
    val asyncContextTaskId = AtomicReference<String>()

    @Once(id = "sync-ctx-task")
    fun syncWithContext(ctx: TaskExecutionContext) {
        syncContextTaskId.set(ctx.taskId)
    }

    @Once(id = "async-ctx-task")
    suspend fun asyncWithContext(ctx: TaskExecutionContext) {
        asyncContextTaskId.set(ctx.taskId)
    }
}

class ContextGenerationTest {

    private val now = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun `generated binding passes TaskExecutionContext to normal function`() = runBlocking {
        val target = ContextTarget()
        val definitions = ContextTarget_ChronoTaskBinding.definitions(target)

        val def = definitions.first { it.id == "sync-ctx-task" }
        val execution = TaskExecution(ExecutionId.generate(), "sync-ctx-task", ScheduledTrigger(now, now))

        val result = DefaultTaskRunner.run(def, execution, null, null)
        assertTrue(result is ExecutionResult.Success)
        assertEquals("sync-ctx-task", target.syncContextTaskId.get())
    }

    @Test
    fun `generated binding passes TaskExecutionContext to suspend function`() = runBlocking {
        val target = ContextTarget()
        val definitions = ContextTarget_ChronoTaskBinding.definitions(target)

        val def = definitions.first { it.id == "async-ctx-task" }
        val execution = TaskExecution(ExecutionId.generate(), "async-ctx-task", ScheduledTrigger(now, now))

        val result = DefaultTaskRunner.run(def, execution, null, null)
        assertTrue(result is ExecutionResult.Success)
        assertEquals("async-ctx-task", target.asyncContextTaskId.get())
    }
}

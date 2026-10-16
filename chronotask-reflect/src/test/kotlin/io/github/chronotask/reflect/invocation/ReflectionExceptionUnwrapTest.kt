package io.github.chronotask.reflect.invocation

import io.github.chronotask.execution.DefaultTaskRunner
import io.github.chronotask.execution.ExecutionId
import io.github.chronotask.execution.ExecutionResult
import io.github.chronotask.execution.TaskExecution
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.reflect.ReflectionMethodAdapter
import io.github.chronotask.trigger.ScheduledTrigger
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.io.FileNotFoundException
import java.time.Instant

class ReflectionExceptionUnwrapTest {

    private val now = Instant.parse("2026-01-01T00:00:00Z")

    class ThrowingService {
        fun throwCheckedException() {
            throw FileNotFoundException("config.json not found")
        }

        suspend fun throwSuspendException() {
            throw IllegalStateException("invalid-state")
        }
    }

    @Test
    fun `sync method unwraps underlying FileNotFoundException`() = runBlocking {
        val service = ThrowingService()
        val method = service.javaClass.getMethod("throwCheckedException")
        val adapter = ReflectionMethodAdapter(service, method)

        val taskDef = TaskDefinition(id = "unwrap-sync", schedule = TaskSchedule.Once(0), executable = adapter)
        val execution = TaskExecution(executionId = ExecutionId.generate(), taskId = "unwrap-sync", trigger = ScheduledTrigger(now, now))

        val result = DefaultTaskRunner.run(taskDef, execution)

        assertInstanceOf(ExecutionResult.Failed::class.java, result)
        val failed = result as ExecutionResult.Failed
        assertInstanceOf(FileNotFoundException::class.java, failed.error)
        assertEquals("config.json not found", failed.error.message)
    }

    @Test
    fun `suspend method unwraps underlying IllegalStateException`() = runBlocking {
        val service = ThrowingService()
        val method = service.javaClass.methods.first { it.name == "throwSuspendException" }
        val adapter = ReflectionMethodAdapter(service, method)

        val taskDef = TaskDefinition(id = "unwrap-suspend", schedule = TaskSchedule.Once(0), executable = adapter)
        val execution = TaskExecution(executionId = ExecutionId.generate(), taskId = "unwrap-suspend", trigger = ScheduledTrigger(now, now))

        val result = DefaultTaskRunner.run(taskDef, execution)

        assertInstanceOf(ExecutionResult.Failed::class.java, result)
        val failed = result as ExecutionResult.Failed
        assertInstanceOf(IllegalStateException::class.java, failed.error)
        assertEquals("invalid-state", failed.error.message)
    }
}

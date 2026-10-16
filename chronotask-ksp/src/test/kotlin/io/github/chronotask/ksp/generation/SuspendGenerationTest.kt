package io.github.chronotask.ksp.generation

import io.github.chronotask.annotation.Once
import io.github.chronotask.execution.DefaultTaskRunner
import io.github.chronotask.execution.ExecutionId
import io.github.chronotask.execution.ExecutionResult
import io.github.chronotask.execution.TaskExecution
import io.github.chronotask.job.TaskExecutionContext
import io.github.chronotask.trigger.ScheduledTrigger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

class SuspendTarget {
    val executed = AtomicBoolean(false)

    @Once(id = "suspend-no-arg")
    suspend fun suspendNoArg() {
        delay(10)
        executed.set(true)
    }

    @Once(id = "suspend-throw")
    suspend fun suspendThrow() {
        delay(5)
        throw IOException("suspend-io-error")
    }

    @Once(id = "suspend-cancel")
    suspend fun suspendCancel() {
        delay(5)
        throw CancellationException("coroutine-cancelled")
    }
}

class SuspendGenerationTest {

    private val now = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun `generated binding executes suspend function directly without reflection`() = runBlocking {
        val target = SuspendTarget()
        val definitions = SuspendTarget_ChronoTaskBinding.definitions(target)

        val def = definitions.first { it.id == "suspend-no-arg" }
        val execution = TaskExecution(ExecutionId.generate(), "suspend-no-arg", ScheduledTrigger(now, now))

        val result = DefaultTaskRunner.run(def, execution, null, null)
        assertTrue(result is ExecutionResult.Success)
        assertTrue(target.executed.get())
    }

    @Test
    fun `generated binding propagates business exception to TaskRunner`() = runBlocking {
        val target = SuspendTarget()
        val definitions = SuspendTarget_ChronoTaskBinding.definitions(target)

        val def = definitions.first { it.id == "suspend-throw" }
        val execution = TaskExecution(ExecutionId.generate(), "suspend-throw", ScheduledTrigger(now, now))

        val result = DefaultTaskRunner.run(def, execution, null, null)
        assertTrue(result is ExecutionResult.Failed)
        val failed = result as ExecutionResult.Failed
        assertTrue(failed.error is IOException)
        assertEquals("suspend-io-error", failed.error.message)
    }

    @Test
    fun `generated binding rethrows CancellationException directly preserving coroutine cancellation`() {
        val target = SuspendTarget()
        val definitions = SuspendTarget_ChronoTaskBinding.definitions(target)

        val def = definitions.first { it.id == "suspend-cancel" }
        val execution = TaskExecution(ExecutionId.generate(), "suspend-cancel", ScheduledTrigger(now, now))

        assertThrows(CancellationException::class.java) {
            runBlocking {
                DefaultTaskRunner.run(def, execution, null, null)
            }
        }
    }
}

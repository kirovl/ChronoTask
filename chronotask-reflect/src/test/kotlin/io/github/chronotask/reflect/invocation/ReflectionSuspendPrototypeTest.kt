package io.github.chronotask.reflect.invocation

import io.github.chronotask.execution.DefaultTaskRunner
import io.github.chronotask.execution.ExecutionId
import io.github.chronotask.execution.ExecutionListener
import io.github.chronotask.execution.ExecutionResult
import io.github.chronotask.execution.TaskExecution
import io.github.chronotask.job.TaskExecutionContext
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.reflect.ReflectionMethodAdapter
import io.github.chronotask.trigger.ScheduledTrigger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class ReflectionSuspendPrototypeTest {

    private val now = Instant.parse("2026-01-01T00:00:00Z")

    class TestSuspendService {
        val completed = AtomicBoolean(false)
        val contextRef = AtomicReference<TaskExecutionContext?>()
        val suspendGate = CompletableDeferred<Unit>()

        suspend fun suspendTask() {
            delay(10)
            completed.set(true)
        }

        suspend fun suspendWithContext(ctx: TaskExecutionContext) {
            contextRef.set(ctx)
            delay(10)
            completed.set(true)
        }

        suspend fun suspendThrowing() {
            delay(5)
            throw IOException("custom-io-failure")
        }

        suspend fun suspendWaitingForCancel() {
            suspendGate.complete(Unit)
            delay(100_000) // indefinite wait
        }

        fun normalThrowing() {
            throw IllegalArgumentException("sync-error")
        }
    }

    private fun createDummyContext(taskId: String): TaskExecutionContext {
        return TaskExecutionContext(
            taskId = taskId,
            trigger = ScheduledTrigger(scheduledAt = now, discoveredAt = now)
        )
    }

    private fun createDummyExecution(taskId: String): TaskExecution {
        return TaskExecution(
            executionId = ExecutionId.generate(),
            taskId = taskId,
            trigger = ScheduledTrigger(scheduledAt = now, discoveredAt = now)
        )
    }

    @Test
    fun `prototype suspend function executes, suspends, and completes`() = runBlocking {
        val service = TestSuspendService()
        val method = service.javaClass.methods.first { it.name == "suspendTask" }
        val adapter = ReflectionMethodAdapter(service, method)

        val taskDef = TaskDefinition(
            id = "proto-suspend",
            name = "Proto Suspend",
            schedule = TaskSchedule.Once(0),
            executable = adapter
        )

        val execution = createDummyExecution("proto-suspend")
        val result = DefaultTaskRunner.run(taskDef, execution)

        assertInstanceOf(ExecutionResult.Success::class.java, result)
        assertTrue(service.completed.get())
    }

    @Test
    fun `prototype suspend function receives TaskExecutionContext`() = runBlocking {
        val service = TestSuspendService()
        val method = service.javaClass.methods.first { it.name == "suspendWithContext" }
        val adapter = ReflectionMethodAdapter(service, method)

        val taskDef = TaskDefinition(
            id = "proto-ctx",
            name = "Proto Ctx",
            schedule = TaskSchedule.Once(0),
            executable = adapter
        )

        val execution = createDummyExecution("proto-ctx")
        val result = DefaultTaskRunner.run(taskDef, execution)

        assertInstanceOf(ExecutionResult.Success::class.java, result)
        assertTrue(service.completed.get())
        assertEquals("proto-ctx", service.contextRef.get()?.taskId)
    }

    @Test
    fun `prototype suspend function unwraps user exceptions directly to TaskRunner`() = runBlocking {
        val service = TestSuspendService()
        val method = service.javaClass.methods.first { it.name == "suspendThrowing" }
        val adapter = ReflectionMethodAdapter(service, method)

        val taskDef = TaskDefinition(
            id = "proto-throw",
            name = "Proto Throw",
            schedule = TaskSchedule.Once(0),
            executable = adapter
        )

        var reportedError: Throwable? = null
        val listener = object : ExecutionListener {
            override fun onFailure(task: TaskDefinition, execution: TaskExecution, error: Throwable) {
                reportedError = error
            }
        }

        val execution = createDummyExecution("proto-throw")
        val result = DefaultTaskRunner.run(taskDef, execution, listener = listener)

        assertInstanceOf(ExecutionResult.Failed::class.java, result)
        val failed = result as ExecutionResult.Failed
        assertInstanceOf(IOException::class.java, failed.error)
        assertEquals("custom-io-failure", failed.error.message)
        assertEquals("custom-io-failure", reportedError?.message)
    }

    @Test
    fun `prototype suspend function cancellation propagates CancellationException seamlessly`() = runBlocking {
        val service = TestSuspendService()
        val method = service.javaClass.methods.first { it.name == "suspendWaitingForCancel" }
        val adapter = ReflectionMethodAdapter(service, method)

        val taskDef = TaskDefinition(
            id = "proto-cancel",
            name = "Proto Cancel",
            schedule = TaskSchedule.Once(0),
            executable = adapter
        )

        var cancelledReported = false
        val listener = object : ExecutionListener {
            override fun onCancelled(task: TaskDefinition, execution: TaskExecution) {
                cancelledReported = true
            }
        }

        val execution = createDummyExecution("proto-cancel")
        val runnerJob = launch(Dispatchers.Default) {
            DefaultTaskRunner.run(taskDef, execution, listener = listener)
        }

        // Wait until task starts and hits suspension
        service.suspendGate.await()

        // Cancel runner job
        runnerJob.cancel()
        runnerJob.join()

        assertTrue(cancelledReported, "TaskRunner must have reported cancellation")
    }

    @Test
    fun `prototype blocking function unwraps InvocationTargetException`() = runBlocking {
        val service = TestSuspendService()
        val method = service.javaClass.getMethod("normalThrowing")
        val adapter = ReflectionMethodAdapter(service, method)

        val taskDef = TaskDefinition(
            id = "proto-sync-throw",
            name = "Proto Sync Throw",
            schedule = TaskSchedule.Once(0),
            executable = adapter
        )

        val execution = createDummyExecution("proto-sync-throw")
        val result = DefaultTaskRunner.run(taskDef, execution)

        assertInstanceOf(ExecutionResult.Failed::class.java, result)
        val failed = result as ExecutionResult.Failed
        assertInstanceOf(IllegalArgumentException::class.java, failed.error)
        assertEquals("sync-error", failed.error.message)
    }
}

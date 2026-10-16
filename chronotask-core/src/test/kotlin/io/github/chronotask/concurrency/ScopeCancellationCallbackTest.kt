package io.github.chronotask.concurrency

import io.github.chronotask.execution.ExecutionListener
import io.github.chronotask.execution.TaskExecution
import io.github.chronotask.model.ConcurrencyPolicy
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.trigger.ScheduledTrigger
import io.github.chronotask.trigger.TriggerSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class ScopeCancellationCallbackTest {
    enum class Policy(val value: ConcurrencyPolicy) {
        ALLOW(ConcurrencyPolicy.Allow),
        SKIP(ConcurrencyPolicy.SkipIfRunning),
        QUEUE_ONE(ConcurrencyPolicy.QueueOne),
        SERIAL(ConcurrencyPolicy.serial(4))
    }

    @ParameterizedTest
    @EnumSource(Policy::class)
    fun `already cancelled parent notifies outside lock and cleans up`(policy: Policy) {
        val worker = Executors.newSingleThreadExecutor()
        val failure = AtomicReference<Throwable?>()
        val cancellationCount = AtomicInteger()
        val executed = AtomicBoolean()
        val parent = Job().apply { cancel() }
        lateinit var controller: DefaultConcurrencyController
        val listener = object : ExecutionListener {
            override fun onCancelled(definition: TaskDefinition, execution: TaskExecution) {
                cancellationCount.incrementAndGet()
                try {
                    val queued = worker.submit<Int> { controller.queuedCount(definition.id) }.get(5, TimeUnit.SECONDS)
                    assertEquals(0, queued)
                } catch (error: Throwable) {
                    failure.set(error)
                }
            }
        }
        controller = DefaultConcurrencyController(
            coroutineScope = CoroutineScope(parent + Dispatchers.Unconfined), listener = listener
        )
        val definition = TaskDefinition(
            id = "cancelled-scope", schedule = TaskSchedule.Once(), concurrencyPolicy = policy.value,
            executable = { executed.set(true) }
        )
        try {
            val execution = TaskExecution(
                taskId = definition.id,
                trigger = ScheduledTrigger(Instant.EPOCH, Instant.EPOCH, TriggerSource.MANUAL)
            )
            runBlocking { controller.submit(definition, execution) }
            assertEquals(1, cancellationCount.get())
            assertNull(failure.get(), "Cancellation listener must query state from another thread without blocking")
            assertFalse(executed.get())
            assertEquals(0, controller.activeCount(definition.id))
            assertEquals(0, controller.queuedCount(definition.id))
            assertEquals(0, controller.cancelAll(definition.id))
            assertEquals(1, cancellationCount.get(), "Cleanup must not repeat the terminal notification")
        } finally {
            controller.close()
            worker.shutdownNow()
        }
    }
}

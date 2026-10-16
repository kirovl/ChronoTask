package io.github.chronotask.concurrency
import io.github.chronotask.concurrency.DefaultConcurrencyController
import io.github.chronotask.execution.ExecutionListener
import io.github.chronotask.execution.TaskExecution
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.trigger.ScheduledTrigger
import io.github.chronotask.trigger.TriggerSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
class CancellationCallbackLockTest {
    @Test fun `cancellation callback can read queue from another thread`() {
        val worker = Executors.newSingleThreadExecutor()
        val failure = AtomicReference<Throwable?>()
        val called = AtomicBoolean()
        lateinit var controller: DefaultConcurrencyController
        val listener = object : ExecutionListener {
            override fun onCancelled(definition: TaskDefinition, execution: TaskExecution) {
                called.set(true)
                try { worker.submit<Int> { controller.queuedCount(definition.id) }.get(5, TimeUnit.SECONDS) }
                catch (error: Throwable) { failure.set(error) }
            }
        }
        controller = DefaultConcurrencyController(dispatcher = Dispatchers.Unconfined, listener = listener)
        try {
            val now = Instant.EPOCH
            val execution = TaskExecution(taskId = "lock-test", trigger = ScheduledTrigger(now, now, TriggerSource.MANUAL))
            val definition = TaskDefinition(id = "lock-test", schedule = TaskSchedule.Once(), executable = { awaitCancellation() })
            runBlocking { controller.submit(definition, execution) }
            assertTrue(controller.cancelExecution(definition.id, execution.executionId))
            assertTrue(called.get())
            assertNull(failure.get(), "Listener cannot read queue while cancellation holds state.lock")
        } finally { controller.close(); worker.shutdownNow() }
    }
}

package io.github.chronotask.engine

import io.github.chronotask.execution.SubmissionResult
import io.github.chronotask.model.ConcurrencyPolicy
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.model.TaskState
import io.github.chronotask.trigger.TriggerSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class TaskHandleTest {

    @Test
    fun `task pause and resume works independently of engine state`() {
        val engine = ChronoTaskEngine.create()
        engine.start()

        val def = TaskDefinition("handle-task", "Handle Task", TaskSchedule.FixedRate(1000L, 1000L), executable = {})
        val handle = engine.register(def)

        assertEquals(TaskState.SCHEDULED, handle.state)
        handle.pause()
        assertEquals(TaskState.PAUSED, handle.state)

        handle.resume()
        assertEquals(TaskState.SCHEDULED, handle.state)

        handle.cancel()
        assertEquals(TaskState.CANCELLED, handle.state)

        // Cancelled task cannot be resumed
        handle.resume()
        assertEquals(TaskState.CANCELLED, handle.state)

        engine.stop()
    }

    @Test
    fun `triggerNow executes immediately with TriggerSource MANUAL`() = runBlocking {
        val executedCount = AtomicInteger(0)
        val execution = CompletableDeferred<TriggerSource>()
        val engine = ChronoTaskEngine.create()
        engine.start()

        val def = TaskDefinition(
            id = "manual-task",
            name = "Manual Task",
            schedule = TaskSchedule.FixedRate(10000L, initialDelayMs = 10000L),
            executable = {
                executedCount.incrementAndGet()
                execution.complete(it.trigger.source)
            }
        )

        val handle = engine.register(def)
        val result = handle.triggerNow()

        assertTrue(result is SubmissionResult.Started)
        assertEquals(TriggerSource.MANUAL, withTimeout(5000) { execution.await() })
        assertEquals(1, executedCount.get())

        engine.stop()
    }

    @Test
    fun `triggerNow allowed while task is paused`() = runBlocking {
        val executedCount = AtomicInteger(0)
        val execution = CompletableDeferred<TriggerSource>()
        val engine = ChronoTaskEngine.create()
        engine.start()

        val def = TaskDefinition(
            id = "paused-manual-task",
            name = "Paused Manual Task",
            schedule = TaskSchedule.FixedRate(10000L, initialDelayMs = 10000L),
            executable = {
                executedCount.incrementAndGet()
                execution.complete(it.trigger.source)
            }
        )

        val handle = engine.register(def)
        handle.pause()

        val result = handle.triggerNow()
        assertTrue(result is SubmissionResult.Started)

        assertEquals(TriggerSource.MANUAL, withTimeout(5000) { execution.await() })
        assertEquals(1, executedCount.get())

        engine.stop()
    }

    @Test
    fun `triggerNow rejected after cancel or stop`() {
        val engine = ChronoTaskEngine.create()
        engine.start()

        val def = TaskDefinition("cancelled-task", "Cancelled Task", TaskSchedule.FixedRate(1000L), executable = {})
        val handle = engine.register(def)
        handle.cancel()

        val res1 = handle.triggerNow()
        assertTrue(res1 is SubmissionResult.Rejected)

        engine.stop()
        val res2 = handle.triggerNow()
        assertTrue(res2 is SubmissionResult.Rejected)
    }

    @Test
    fun `snapshot provides consistent point-in-time status`() {
        val engine = ChronoTaskEngine.create()
        val def = TaskDefinition("snapshot-task", "Snapshot Task", TaskSchedule.FixedRate(5000L), executable = {})
        val handle = engine.register(def)

        val snapshot = handle.snapshot()
        assertEquals("snapshot-task", snapshot.taskId)
        assertEquals(TaskState.REGISTERED, snapshot.state)
        assertEquals(0, snapshot.activeExecutions)
        assertEquals(false, snapshot.scheduleCompleted)

        engine.stop()
    }
}

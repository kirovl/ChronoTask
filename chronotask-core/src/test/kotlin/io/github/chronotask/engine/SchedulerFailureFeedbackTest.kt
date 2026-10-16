package io.github.chronotask.engine

import io.github.chronotask.clock.ChronoClock
import io.github.chronotask.clock.ChronoTimer
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.test.time.VirtualTimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant

class SchedulerFailureFeedbackTest {
    private val base = Instant.parse("2026-01-01T00:00:00Z")
    private val dispatcher = DefaultDispatcherProvider(Dispatchers.Unconfined)

    private fun verifyTimerFailure(skipMisfire: Boolean) {
        val time = VirtualTimeSource(base)
        val error = IllegalStateException("timer unavailable")
        var healthyCalls = 0
        DefaultChronoTaskEngine(EngineConfig(
            clock = time.clock, dispatcherProvider = dispatcher,
            timer = object : ChronoTimer {
                override suspend fun delay(millis: Long) { throw error }
            }
        )).use { engine ->
            val schedule = if (skipMisfire) TaskSchedule.FixedRate(1000) else TaskSchedule.Once(1000)
            val bad = engine.register(TaskDefinition("timer-failure", schedule = schedule, executable = {}))
            if (skipMisfire) time.advanceBy(100)
            engine.register(TaskDefinition("healthy", schedule = TaskSchedule.Once(),
                executable = { healthyCalls++ }))
            engine.start()
            assertSame(error, bad.snapshot().scheduleFailure)
            assertTrue(bad.snapshot().scheduleCompleted)
            assertNull(bad.snapshot().nextScheduledAt)
            assertEquals(1, healthyCalls)
        }
    }

    @Test fun `timer failure while waiting for a trigger is recorded`() = verifyTimerFailure(false)
    @Test fun `timer failure after skipping misfires is recorded`() = verifyTimerFailure(true)

    @Test fun `clock failure in scheduler is recorded`() {
        val error = IllegalStateException("clock unavailable")
        var reads = 0
        val clock = ChronoClock {
            if (++reads >= 2) throw error
            base
        }
        DefaultChronoTaskEngine(EngineConfig(clock = clock, dispatcherProvider = dispatcher)).use { engine ->
            val bad = engine.register(TaskDefinition("clock-failure", schedule = TaskSchedule.Once(1000),
                executable = {}))
            engine.start()
            assertSame(error, bad.snapshot().scheduleFailure)
            assertTrue(bad.snapshot().scheduleCompleted)
            assertNull(bad.snapshot().nextScheduledAt)
        }
    }

    @Test fun `calculator cancellation does not mark failure and can resume`() {
        val time = VirtualTimeSource(base)
        var first = true
        var calls = 0
        DefaultChronoTaskEngine(EngineConfig(
            clock = time.clock, timer = time.timer, dispatcherProvider = dispatcher
        )).use { engine ->
            val handle = engine.register(TaskDefinition("cancel-calculation", schedule = TaskSchedule.Custom { context ->
                if (first) { first = false; throw CancellationException("calculation cancelled") }
                if (context.lastScheduledAt == null) base.plusSeconds(1) else null
            }, executable = { calls++ }))
            engine.start()
            assertFalse(handle.snapshot().scheduleCompleted)
            assertNull(handle.snapshot().scheduleFailure)
            handle.resume()
            time.advanceBy(1000)
            assertEquals(1, calls)
            assertTrue(handle.snapshot().scheduleCompleted)
            assertNull(handle.snapshot().scheduleFailure)
        }
    }

    @Test fun `old timer failure cannot overwrite resumed scheduler`() {
        val time = VirtualTimeSource(base)
        var first = true
        var calls = 0
        lateinit var handle: TaskHandle
        val timer = object : ChronoTimer {
            override suspend fun delay(millis: Long) {
                if (first) {
                    first = false
                    handle.pause()
                    handle.resume()
                    throw IllegalStateException("old timer failure")
                }
                time.timer.delay(millis)
            }
        }
        DefaultChronoTaskEngine(EngineConfig(
            clock = time.clock, timer = timer, dispatcherProvider = dispatcher
        )).use { engine ->
            handle = engine.register(TaskDefinition("resumed-timer", schedule = TaskSchedule.Once(1000),
                executable = { calls++ }))
            engine.start()
            assertFalse(handle.snapshot().scheduleCompleted)
            assertNull(handle.snapshot().scheduleFailure)
            time.advanceBy(1000)
            assertEquals(1, calls)
            assertTrue(handle.snapshot().scheduleCompleted)
            assertNull(handle.snapshot().scheduleFailure)
        }
    }
}

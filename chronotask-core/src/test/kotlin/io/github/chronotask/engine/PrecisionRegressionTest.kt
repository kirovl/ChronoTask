package io.github.chronotask.engine

import io.github.chronotask.engine.*
import io.github.chronotask.job.TaskExecutable
import io.github.chronotask.model.*
import io.github.chronotask.test.time.VirtualTimeSource
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant

class PrecisionRegressionTest {
    private val base = Instant.parse("2026-01-01T00:00:00Z")

    @Test fun catchUpMustRespectLimitAtSubMillisecondBoundary() {
        val time = VirtualTimeSource(base)
        val calls = mutableListOf<Instant>()
        val engine = DefaultChronoTaskEngine(EngineConfig(clock = time.clock, timer = time.timer,
            dispatcherProvider = DefaultDispatcherProvider(Dispatchers.Unconfined)))
        try {
            engine.register(TaskDefinition("rate", "rate", TaskSchedule.FixedRate(1000),
                ConcurrencyPolicy.ALLOW, MisfirePolicy.CatchUp(2), executable = TaskExecutable { calls += it.trigger.scheduledAt }))
            engine.start()
            engine.pause()
            calls.clear()
            time.advanceTo(base.plusSeconds(3).plusNanos(1))
            engine.resume()
            assertEquals(2, calls.size, "Three missed ticks must yield only the bounded catch-up batch")
        } finally { engine.stop() }
    }

    @Test fun fireOnceMustEmitOnlyTheLatestMissedTickAtSubMillisecondBoundary() {
        val time = VirtualTimeSource(base)
        val calls = mutableListOf<Instant>()
        val engine = DefaultChronoTaskEngine(EngineConfig(clock = time.clock, timer = time.timer,
            dispatcherProvider = DefaultDispatcherProvider(Dispatchers.Unconfined)))
        try {
            engine.register(TaskDefinition("rate", "rate", TaskSchedule.FixedRate(1000),
                ConcurrencyPolicy.ALLOW, MisfirePolicy.FIRE_ONCE, executable = TaskExecutable { calls += it.trigger.scheduledAt }))
            engine.start()
            engine.pause()
            calls.clear()
            time.advanceTo(base.plusSeconds(3).plusNanos(1))
            engine.resume()
            assertEquals(listOf(base.plusSeconds(3)), calls)
        } finally { engine.stop() }
    }

    @Test fun onceTaskMustNotRunBeforeItsScheduledInstant() {
        val time = VirtualTimeSource(base)
        var actual: Instant? = null
        val engine = DefaultChronoTaskEngine(EngineConfig(clock = time.clock, timer = time.timer,
            dispatcherProvider = DefaultDispatcherProvider(Dispatchers.Unconfined)))
        try {
            engine.register(TaskDefinition("once", "once", TaskSchedule.Once(1),
                ConcurrencyPolicy.ALLOW, MisfirePolicy.SKIP, executable = TaskExecutable { actual = time.now() }))
            time.advanceTo(base.plusNanos(500_000))
            engine.start()
            assertNull(actual, "The task is not due until 1 ms, but the clock is only at 0.5 ms")
            time.advanceTo(base.plusMillis(1))
            assertNull(actual)
            time.advanceTo(base.plusMillis(2))
            assertNotNull(actual, "The task must eventually execute after its rounded-up wait")
            assertFalse(actual!!.isBefore(base.plusMillis(1)))
            assertTrue(engine.getHandle("once")!!.snapshot().scheduleCompleted)
        } finally { engine.stop() }
    }
}

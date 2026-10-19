package io.github.chronotask.engine

import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.MisfirePolicy
import io.github.chronotask.model.ConcurrencyPolicy
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.test.time.VirtualTimeSource
import io.github.chronotask.trigger.TriggerSource
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant

class ScheduleActivationTest {
    private val base = Instant.parse("2026-01-01T00:00:00Z")

    private fun schedule(kind: String, delay: Long): TaskSchedule = when (kind) {
        "rate" -> TaskSchedule.FixedRate(1_000, delay)
        "delay" -> TaskSchedule.FixedDelay(1_000, delay)
        else -> TaskSchedule.Once(delay)
    }

    @ParameterizedTest
    @CsvSource("rate,0", "delay,0", "once,0", "rate,500", "delay,500", "once,500")
    fun `initial delay starts when automatic scheduling activates rather than registration`(kind: String, delay: Long) {
        val time = VirtualTimeSource(base)
        val scheduled = mutableListOf<Instant>()
        val sources = mutableListOf<TriggerSource>()
        DefaultChronoTaskEngine(EngineConfig(clock = time.clock, timer = time.timer,
            dispatcherProvider = DefaultDispatcherProvider(Dispatchers.Unconfined))).use { engine ->
            val handle = engine.register(TaskDefinition("activation", schedule = schedule(kind, delay),
                executable = { scheduled.add(it.trigger.scheduledAt); sources.add(it.trigger.source) }))
            time.advanceBy(60_123)
            engine.start()
            if (delay > 0) {
                assertTrue(scheduled.isEmpty())
                assertEquals(base.plusMillis(60_123 + delay), handle.snapshot().nextScheduledAt)
                time.advanceBy(delay - 1)
                assertTrue(scheduled.isEmpty())
                time.advanceBy(1)
            }
            assertEquals(listOf(base.plusMillis(60_123 + delay)), scheduled)
            assertEquals(listOf(TriggerSource.SCHEDULE), sources)
            assertEquals(base, handle.snapshot().registeredAt, "Registration timestamp remains historical")
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["rate", "delay", "once"])
    fun `initially disabled tasks measure their delay from first resume`(kind: String) {
        val time = VirtualTimeSource(base)
        val scheduled = mutableListOf<Instant>()
        DefaultChronoTaskEngine(EngineConfig(clock = time.clock, timer = time.timer,
            dispatcherProvider = DefaultDispatcherProvider(Dispatchers.Unconfined))).use { engine ->
            val handle = engine.register(TaskDefinition("disabled", schedule = schedule(kind, 500),
                isEnabled = false, executable = { scheduled.add(it.trigger.scheduledAt) }))
            engine.start()
            time.advanceBy(60_123)
            handle.resume()
            assertTrue(scheduled.isEmpty())
            time.advanceBy(499)
            assertTrue(scheduled.isEmpty())
            time.advanceBy(1)
            assertEquals(listOf(base.plusMillis(60_623)), scheduled)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["rate", "delay", "once"])
    fun `registration listener latency in running engine does not consume initial delay`(kind: String) {
        val time = VirtualTimeSource(base)
        val scheduled = mutableListOf<Instant>()
        DefaultChronoTaskEngine(EngineConfig(clock = time.clock, timer = time.timer,
            dispatcherProvider = DefaultDispatcherProvider(Dispatchers.Unconfined),
            lifecycleListener = object : TaskLifecycleListener {
                override fun onRegistered(definition: TaskDefinition) { time.advanceBy(1_003) }
            })).use { engine ->
            engine.start()
            engine.register(TaskDefinition("late-registration", schedule = schedule(kind, 500),
                executable = { scheduled.add(it.trigger.scheduledAt) }))
            time.advanceBy(499)
            assertTrue(scheduled.isEmpty())
            time.advanceBy(1)
            assertEquals(listOf(base.plusMillis(1_503)), scheduled)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["rate", "delay", "once"])
    fun `pause before first trigger preserves the existing initial delay`(kind: String) {
        val time = VirtualTimeSource(base)
        val scheduled = mutableListOf<Instant>()
        DefaultChronoTaskEngine(EngineConfig(clock = time.clock, timer = time.timer,
            dispatcherProvider = DefaultDispatcherProvider(Dispatchers.Unconfined))).use { engine ->
            engine.register(TaskDefinition("pause-before-first", schedule = schedule(kind, 1_000),
                executable = { scheduled.add(it.trigger.scheduledAt) }))
            engine.start()
            time.advanceBy(100)
            engine.pause()
            time.advanceBy(200)
            engine.resume()
            time.advanceBy(699)
            assertTrue(scheduled.isEmpty())
            time.advanceBy(1)
            assertEquals(listOf(base.plusMillis(1_000)), scheduled)
        }
    }

    @ParameterizedTest
    @CsvSource("rate,skip", "delay,skip", "once,skip", "rate,fire", "delay,fire", "once,fire",
        "rate,catchup", "delay,catchup", "once,catchup")
    fun `pause across the first deadline still applies the configured misfire policy`(kind: String, policy: String) {
        val time = VirtualTimeSource(base)
        val scheduled = mutableListOf<Instant>()
        val sources = mutableListOf<TriggerSource>()
        val misfire = when (policy) {
            "skip" -> MisfirePolicy.Skip
            "fire" -> MisfirePolicy.FireOnce
            else -> MisfirePolicy.CatchUp(10)
        }
        DefaultChronoTaskEngine(EngineConfig(clock = time.clock, timer = time.timer,
            dispatcherProvider = DefaultDispatcherProvider(Dispatchers.Unconfined))).use { engine ->
            val handle = engine.register(TaskDefinition("paused-deadline", schedule = schedule(kind, 1_000),
                concurrencyPolicy = ConcurrencyPolicy.Allow,
                misfirePolicy = misfire,
                executable = { scheduled.add(it.trigger.scheduledAt); sources.add(it.trigger.source) }))
            engine.start()
            time.advanceBy(50)
            engine.pause()
            time.advanceBy(2_000)
            engine.resume()
            val expected = when {
                policy == "skip" -> emptyList()
                kind == "rate" && policy == "catchup" -> listOf(base.plusMillis(1_000), base.plusMillis(2_000))
                kind == "rate" -> listOf(base.plusMillis(2_000))
                else -> listOf(base.plusMillis(1_000))
            }
            assertEquals(expected, scheduled)
            assertEquals(List(expected.size) { TriggerSource.MISFIRE }, sources)
            if (kind == "once") {
                assertTrue(handle.snapshot().scheduleCompleted)
            } else if (policy == "skip") {
                val next = base.plusMillis(if (kind == "rate") 3_000 else 3_050)
                assertEquals(next, handle.snapshot().nextScheduledAt)
                time.advanceTo(next)
                assertEquals(listOf(next), scheduled)
                assertEquals(listOf(TriggerSource.SCHEDULE), sources)
            }
        }
    }
}

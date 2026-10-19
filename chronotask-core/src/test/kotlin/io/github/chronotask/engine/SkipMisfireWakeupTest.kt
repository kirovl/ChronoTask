package io.github.chronotask.engine

import io.github.chronotask.clock.ChronoTimer
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.test.time.VirtualTimeSource
import io.github.chronotask.trigger.TriggerSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SkipMisfireWakeupTest {
    private val base = Instant.parse("2026-01-01T00:00:00Z")

    @ParameterizedTest
    @ValueSource(strings = ["delay", "rate", "cron", "custom"])
    fun `skip recovery commits its next trigger despite a slightly late timer wakeup`(kind: String) {
        val time = VirtualTimeSource(base)
        var oversleep = false
        var sleeps = 0
        val timer = object : ChronoTimer {
            override suspend fun delay(millis: Long) {
                if (oversleep) {
                    check(++sleeps <= 5) { "Skipped the awaited trigger repeatedly" }
                    time.advanceBy(millis + 3)
                    yield()
                } else time.timer.delay(millis)
            }
        }
        val schedule = when (kind) {
            "delay" -> TaskSchedule.FixedDelay(100)
            "rate" -> TaskSchedule.FixedRate(100)
            "cron" -> TaskSchedule.Cron("* * * * * ?", ZoneOffset.UTC)
            else -> TaskSchedule.Custom { context ->
                context.lastScheduledAt?.plusMillis(100) ?: context.registeredAt
            }
        }
        val sources = mutableListOf<TriggerSource>()
        val scheduled = mutableListOf<Instant>()
        DefaultChronoTaskEngine(EngineConfig(clock = time.clock, timer = timer,
            dispatcherProvider = DefaultDispatcherProvider(Dispatchers.Unconfined))).use { engine ->
            val handle = engine.register(TaskDefinition("skip-wakeup", schedule = schedule,
                executable = {
                    scheduled.add(it.trigger.scheduledAt)
                    sources.add(it.trigger.source)
                    if (scheduled.size == 3) engine.stop()
                }))
            engine.start()
            if (kind == "cron") time.advanceBy(1_000)
            assertEquals(1, scheduled.size)
            engine.pause()
            time.advanceBy(1_001)
            oversleep = true
            engine.resume()
            assertNull(handle.snapshot().scheduleFailure)
            assertEquals(3, scheduled.size)
            assertTrue(scheduled.zipWithNext().all { (previous, next) -> next.isAfter(previous) })
            assertEquals(List(3) { TriggerSource.SCHEDULE }, sources)
        }
    }

    @Test
    fun `default real clock fixed delay task executes repeatedly after delayed startup`() {
        val latch = CountDownLatch(3)
        DefaultChronoTaskEngine().use { engine ->
            engine.register(TaskDefinition("real-clock", schedule = TaskSchedule.FixedDelay(25),
                executable = { latch.countDown() }))
            Thread.sleep(10)
            engine.start()
            assertTrue(latch.await(5, TimeUnit.SECONDS), "Default SKIP must not starve a fixed-delay task")
        }
    }
}

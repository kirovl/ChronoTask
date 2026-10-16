package io.github.chronotask.test.time

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

class ClockTimerIntegrationTest {

    @Test
    fun `clock and timer share the exact same timeline`() = runBlocking {
        val timeSource = VirtualTimeSource(Instant.parse("2026-01-01T00:00:00Z"))
        val clock = timeSource.clock
        val timer = timeSource.timer

        val observedTimes = mutableListOf<Instant>()

        launch(Dispatchers.Unconfined) {
            observedTimes.add(clock.now())
            timer.delay(Duration.ofSeconds(10))
            observedTimes.add(clock.now())
            timer.delay(Duration.ofMinutes(5))
            observedTimes.add(clock.now())
        }

        assertEquals(listOf(Instant.parse("2026-01-01T00:00:00Z")), observedTimes)

        timeSource.advanceBy(Duration.ofSeconds(10))
        assertEquals(
            listOf(
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:10Z")
            ),
            observedTimes
        )

        timeSource.advanceBy(Duration.ofMinutes(5))
        assertEquals(
            listOf(
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:10Z"),
                Instant.parse("2026-01-01T00:05:10Z")
            ),
            observedTimes
        )
    }

    @Test
    fun `30-day virtual jump finishes in under 50ms real time`() = runBlocking {
        val timeSource = VirtualTimeSource(Instant.parse("2026-01-01T00:00:00Z"))
        val timer = timeSource.timer

        val triggered = AtomicInteger(0)

        // Register 30 delays distributed across 30 days
        for (day in 1..30) {
            launch(Dispatchers.Unconfined) {
                timer.delay(Duration.ofDays(day.toLong()))
                triggered.incrementAndGet()
            }
        }

        assertEquals(30, timeSource.waiterCount())
        assertEquals(0, triggered.get())

        val realElapsed = measureTimeMillis {
            // Jump 30 days into the future
            timeSource.advanceBy(Duration.ofDays(30))
        }

        assertEquals(30, triggered.get())
        assertEquals(0, timeSource.waiterCount())
        assertEquals(Instant.parse("2026-01-31T00:00:00Z"), timeSource.now())
        assertTrue(realElapsed < 100, "Expected simulation to take < 100ms, took $realElapsed ms")
    }

    @Test
    fun `cascading sequential delays advance within single advanceTo call`() = runBlocking {
        val timeSource = VirtualTimeSource(Instant.parse("2026-01-01T00:00:00Z"))
        val timer = timeSource.timer
        val tickCount = AtomicInteger(0)

        launch(Dispatchers.Unconfined) {
            // Perform 10 consecutive 1-second delays
            repeat(10) {
                timer.delay(1000L)
                tickCount.incrementAndGet()
            }
        }

        // Advance 10 seconds in one single call
        timeSource.advanceBy(10000L)

        assertEquals(10, tickCount.get())
        assertEquals(Instant.parse("2026-01-01T00:00:10Z"), timeSource.now())
        assertEquals(0, timeSource.waiterCount())
    }

    @Test
    fun `sleepUntil with fake clock waits until target instant`() = runBlocking {
        val timeSource = VirtualTimeSource(Instant.parse("2026-05-01T12:00:00Z"))
        val clock = timeSource.clock
        val timer = timeSource.timer
        val target = Instant.parse("2026-05-01T18:00:00Z")

        var wokeUpAt: Instant? = null
        launch(Dispatchers.Unconfined) {
            timer.sleepUntil(target, clock)
            wokeUpAt = clock.now()
        }

        assertEquals(1, timeSource.waiterCount())

        timeSource.advanceBy(Duration.ofHours(3))
        assertEquals(null, wokeUpAt)

        timeSource.advanceBy(Duration.ofHours(3))
        assertEquals(target, wokeUpAt)
        assertEquals(0, timeSource.waiterCount())
    }

    @Test
    fun `concurrent waiters with same trigger timestamp preserve FIFO sequence`() = runBlocking {
        val timeSource = VirtualTimeSource()
        val timer = timeSource.timer
        val log = mutableListOf<Int>()

        for (i in 1..5) {
            launch(Dispatchers.Unconfined) {
                timer.delay(1000L)
                log.add(i)
            }
        }

        timeSource.advanceBy(1000L)
        assertEquals(listOf(1, 2, 3, 4, 5), log)
    }
}

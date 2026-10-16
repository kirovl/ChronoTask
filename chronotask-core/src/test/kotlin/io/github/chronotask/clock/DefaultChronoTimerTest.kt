package io.github.chronotask.clock

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

class DefaultChronoTimerTest {

    @Test
    fun `system companion property references default timer`() {
        assertSame(DefaultChronoTimer, ChronoTimer.SYSTEM)
    }

    @Test
    fun `delay with negative millis throws IllegalArgumentException`() = runBlocking<Unit> {
        assertThrows<IllegalArgumentException> {
            DefaultChronoTimer.delay(-1L)
        }
    }

    @Test
    fun `delay with negative java duration throws IllegalArgumentException`() = runBlocking<Unit> {
        assertThrows<IllegalArgumentException> {
            DefaultChronoTimer.delay(Duration.ofMillis(-50))
        }
    }

    @Test
    fun `delay with negative kotlin duration throws IllegalArgumentException`() = runBlocking<Unit> {
        assertThrows<IllegalArgumentException> {
            DefaultChronoTimer.delay((-50).milliseconds)
        }
    }

    @Test
    fun `delay rejects negative java sub-millisecond duration`() = runBlocking<Unit> {
        assertThrows<IllegalArgumentException> {
            DefaultChronoTimer.delay(Duration.ofNanos(-1))
        }
    }

    @Test
    fun `delay rejects negative kotlin sub-millisecond duration`() = runBlocking<Unit> {
        assertThrows<IllegalArgumentException> {
            DefaultChronoTimer.delay((-1).nanoseconds)
        }
    }

    @Test
    fun `delay zero completes immediately`() = runBlocking {
        DefaultChronoTimer.delay(0L)
        DefaultChronoTimer.delay(Duration.ZERO)
        DefaultChronoTimer.delay(kotlin.time.Duration.ZERO)
    }

    @Test
    fun `sleepUntil with past instant returns immediately`() = runBlocking {
        val pastClock = ChronoClock { Instant.parse("2026-01-01T12:00:00Z") }
        val targetInstant = Instant.parse("2026-01-01T11:00:00Z")

        val start = System.currentTimeMillis()
        DefaultChronoTimer.sleepUntil(targetInstant, pastClock)
        val elapsed = System.currentTimeMillis() - start

        assertTrue(elapsed < 100)
    }

    @Test
    fun `short delay suspends approximately requested time`() = runBlocking {
        val start = System.currentTimeMillis()
        DefaultChronoTimer.delay(20L)
        val elapsed = System.currentTimeMillis() - start
        assertTrue(elapsed >= 15, "Expected at least ~15ms, got $elapsed ms")
    }

    @Test
    fun `sleepUntil rounds up and retries when a timer wakes early`() = runBlocking {
        val base = Instant.parse("2026-01-01T00:00:00Z")
        var now = base
        val waits = mutableListOf<Long>()
        val timer = object : ChronoTimer {
            override suspend fun delay(millis: Long) {
                waits += millis
                now = now.plusMillis(if (waits.size == 1) 1 else millis)
            }
        }
        val target = base.plusNanos(1_500_000)
        timer.sleepUntil(target, ChronoClock { now })
        assertEquals(listOf(2L, 1L), waits)
        assertTrue(now >= target)
    }

    @Test
    fun `sleepUntil waits again when the clock moves backwards`() = runBlocking {
        val base = Instant.parse("2026-01-01T00:00:00Z")
        var now = base
        val waits = mutableListOf<Long>()
        val timer = object : ChronoTimer {
            override suspend fun delay(millis: Long) {
                waits += millis
                now = if (waits.size == 1) base.minusMillis(1) else now.plusMillis(millis)
            }
        }
        val target = base.plusMillis(2)
        timer.sleepUntil(target, ChronoClock { now })
        assertEquals(listOf(2L, 3L), waits)
        assertEquals(target, now)
    }
}

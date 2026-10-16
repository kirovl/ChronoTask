package io.github.chronotask.test.time

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

class FakeChronoClockTest {

    @Test
    fun `fake clock delegates to underlying virtual time source`() {
        val timeSource = VirtualTimeSource(Instant.parse("2026-03-01T00:00:00Z"))
        val clock = FakeChronoClock(timeSource)
        assertSame(timeSource, clock.timeSource)

        assertEquals(Instant.parse("2026-03-01T00:00:00Z"), clock.now())
        assertEquals(Instant.parse("2026-03-01T00:00:00Z").toEpochMilli(), clock.currentTimeMillis())

        clock.advanceBy(1000L)
        assertEquals(Instant.parse("2026-03-01T00:00:01Z"), clock.now())

        clock.advanceBy(Duration.ofMinutes(5))
        assertEquals(Instant.parse("2026-03-01T00:05:01Z"), clock.now())

        clock.advanceBy(30.seconds)
        assertEquals(Instant.parse("2026-03-01T00:05:31Z"), clock.now())

        clock.advanceTo(Instant.parse("2026-03-01T01:00:00Z"))
        assertEquals(Instant.parse("2026-03-01T01:00:00Z"), clock.now())
    }

    @Test
    fun `constructor with initial instant initializes internal time source`() {
        val initial = Instant.parse("2026-07-07T07:07:07Z")
        val clock = FakeChronoClock(initial)
        assertEquals(initial, clock.now())
    }
}

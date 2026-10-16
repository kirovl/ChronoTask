package io.github.chronotask.clock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.math.abs

class DefaultChronoClockTest {

    @Test
    fun `default clock returns current instant and millis`() {
        val before = System.currentTimeMillis()
        val clockInstant = DefaultChronoClock.now()
        val clockMillis = DefaultChronoClock.currentTimeMillis()
        val after = System.currentTimeMillis()

        assertTrue(clockInstant.toEpochMilli() in before..after)
        assertTrue(clockMillis in before..after)
        assertTrue(abs(clockInstant.toEpochMilli() - clockMillis) <= 5)
    }

    @Test
    fun `system companion property references default clock`() {
        assertSame(DefaultChronoClock, ChronoClock.SYSTEM)
    }

    @Test
    fun `custom clock lambda functions correctly`() {
        val fixedInstant = Instant.parse("2026-06-01T12:00:00Z")
        val customClock = ChronoClock { fixedInstant }

        assertEquals(fixedInstant, customClock.now())
        assertEquals(fixedInstant.toEpochMilli(), customClock.currentTimeMillis())
    }
}

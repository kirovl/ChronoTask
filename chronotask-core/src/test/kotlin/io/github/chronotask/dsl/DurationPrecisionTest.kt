package io.github.chronotask.dsl

import io.github.chronotask.exception.TaskConfigurationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration as JavaDuration
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

class DurationPrecisionTest {
    @Test fun `whole millisecond durations beyond nanosecond range remain valid`() {
        for (duration in listOf(146_000.days, (Long.MAX_VALUE / 4).milliseconds)) {
            val expected = duration.inWholeMilliseconds
            assertEquals(expected, duration.toScheduleIntervalMs())
            assertEquals(expected, duration.toScheduleDelayMs())
            assertEquals(JavaDuration.ofMillis(expected).toScheduleIntervalMs(), duration.toScheduleIntervalMs())
        }
    }

    @Test fun `sub-millisecond precision remains rejected including fractional milliseconds`() {
        for (duration in listOf(1.nanoseconds, 1_000_001.nanoseconds)) {
            assertThrows<TaskConfigurationException> { duration.toScheduleIntervalMs() }
            assertThrows<TaskConfigurationException> { duration.toScheduleDelayMs() }
        }
        assertEquals(2L, 2.milliseconds.toScheduleIntervalMs())
        assertEquals(0L, Duration.ZERO.toScheduleDelayMs())
    }

    @Test fun `negative zero interval and infinite durations remain rejected`() {
        assertThrows<TaskConfigurationException> { Duration.ZERO.toScheduleIntervalMs() }
        for (duration in listOf((-1).milliseconds, Duration.INFINITE, -Duration.INFINITE)) {
            assertThrows<TaskConfigurationException> { duration.toScheduleIntervalMs() }
            assertThrows<TaskConfigurationException> { duration.toScheduleDelayMs() }
        }
    }
}

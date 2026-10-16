package io.github.chronotask.dsl

import io.github.chronotask.exception.TaskConfigurationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import java.time.Duration as JavaDuration

class TaskDslDurationTest {

    @Test
    fun `converts valid positive kotlin durations to milliseconds`() {
        assertEquals(1L, 1.milliseconds.toScheduleIntervalMs())
        assertEquals(1000L, 1.seconds.toScheduleIntervalMs())
        assertEquals(60000L, 1.minutes.toScheduleIntervalMs())
        assertEquals(3600000L, 1.hours.toScheduleIntervalMs())
        assertEquals(86400000L, 1.days.toScheduleIntervalMs())
    }

    @Test
    fun `converts valid non-negative kotlin delay durations`() {
        assertEquals(0L, Duration.ZERO.toScheduleDelayMs())
        assertEquals(5000L, 5.seconds.toScheduleDelayMs())
    }

    @Test
    fun `rejects non-positive interval durations`() {
        assertThrows(TaskConfigurationException::class.java) {
            Duration.ZERO.toScheduleIntervalMs()
        }
        assertThrows(TaskConfigurationException::class.java) {
            (-5).seconds.toScheduleIntervalMs()
        }
    }

    @Test
    fun `rejects sub-millisecond precision kotlin durations`() {
        assertThrows(TaskConfigurationException::class.java) {
            500.microseconds.toScheduleIntervalMs()
        }
        assertThrows(TaskConfigurationException::class.java) {
            100.nanoseconds.toScheduleIntervalMs()
        }
        assertThrows(TaskConfigurationException::class.java) {
            (1000.milliseconds + 500.microseconds).toScheduleIntervalMs()
        }
    }

    @Test
    fun `rejects infinite kotlin durations`() {
        assertThrows(TaskConfigurationException::class.java) {
            Duration.INFINITE.toScheduleIntervalMs()
        }
        assertThrows(TaskConfigurationException::class.java) {
            Duration.INFINITE.toScheduleDelayMs()
        }
    }

    @Test
    fun `converts valid java durations`() {
        assertEquals(5000L, JavaDuration.ofSeconds(5).toScheduleIntervalMs())
        assertEquals(0L, JavaDuration.ZERO.toScheduleDelayMs())
    }

    @Test
    fun `rejects invalid java durations`() {
        assertThrows(TaskConfigurationException::class.java) {
            JavaDuration.ZERO.toScheduleIntervalMs()
        }
        assertThrows(TaskConfigurationException::class.java) {
            JavaDuration.ofSeconds(-5).toScheduleIntervalMs()
        }
        assertThrows(TaskConfigurationException::class.java) {
            JavaDuration.ofNanos(500_000).toScheduleIntervalMs()
        }
    }
}

package io.github.chronotask.trigger

import io.github.chronotask.model.TaskSchedule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class FixedRateTriggerCalculatorTest {

    @Test
    fun `initialDelay zero triggers at registeredAt`() {
        val registered = Instant.parse("2026-01-01T10:00:00Z")
        val schedule = TaskSchedule.FixedRate(intervalMs = 5000L, initialDelayMs = 0L)
        val calculator = FixedRateTriggerCalculator(schedule)

        val context = TriggerContext(
            now = registered,
            registeredAt = registered,
            lastScheduledAt = null,
            lastStartedAt = null,
            lastCompletedAt = null
        )

        val next = calculator.nextTrigger(context)
        assertEquals(registered, next)
    }

    @Test
    fun `initialDelay 2s triggers at registeredAt plus 2s`() {
        val registered = Instant.parse("2026-01-01T10:00:00Z")
        val schedule = TaskSchedule.FixedRate(intervalMs = 5000L, initialDelayMs = 2000L)
        val calculator = FixedRateTriggerCalculator(schedule)

        val context = TriggerContext(
            now = registered,
            registeredAt = registered,
            lastScheduledAt = null,
            lastStartedAt = null,
            lastCompletedAt = null
        )

        val next = calculator.nextTrigger(context)
        assertEquals(Instant.parse("2026-01-01T10:00:02Z"), next)
    }

    @Test
    fun `interval progression strictly follows lastScheduledAt`() {
        val registered = Instant.parse("2026-01-01T10:00:00Z")
        val schedule = TaskSchedule.FixedRate(intervalMs = 5000L, initialDelayMs = 2000L)
        val calculator = FixedRateTriggerCalculator(schedule)

        var lastScheduled: Instant? = null
        val expected = listOf(
            Instant.parse("2026-01-01T10:00:02Z"),
            Instant.parse("2026-01-01T10:00:07Z"),
            Instant.parse("2026-01-01T10:00:12Z"),
            Instant.parse("2026-01-01T10:00:17Z")
        )

        val actual = mutableListOf<Instant>()
        for (i in 0 until 4) {
            val ctx = TriggerContext(
                now = registered,
                registeredAt = registered,
                lastScheduledAt = lastScheduled,
                lastStartedAt = null,
                lastCompletedAt = null
            )
            val next = calculator.nextTrigger(ctx)
            actual.add(next)
            lastScheduled = next
        }

        assertEquals(expected, actual)
    }

    @Test
    fun `anti-drift guarantee - lastStartedAt and lastCompletedAt do not shift schedule timeline`() {
        val registered = Instant.parse("2026-01-01T10:00:00Z")
        val schedule = TaskSchedule.FixedRate(intervalMs = 5000L, initialDelayMs = 2000L)
        val calculator = FixedRateTriggerCalculator(schedule)

        // Scheduled at 10:00:07, but delayed start at 10:00:09, completed at 10:00:11
        val context = TriggerContext(
            now = Instant.parse("2026-01-01T10:00:11Z"),
            registeredAt = registered,
            lastScheduledAt = Instant.parse("2026-01-01T10:00:07Z"),
            lastStartedAt = Instant.parse("2026-01-01T10:00:09Z"),
            lastCompletedAt = Instant.parse("2026-01-01T10:00:11Z")
        )

        val next = calculator.nextTrigger(context)
        // Must still be 10:00:12, NOT 10:00:14 or 10:00:16
        assertEquals(Instant.parse("2026-01-01T10:00:12Z"), next)
    }
}

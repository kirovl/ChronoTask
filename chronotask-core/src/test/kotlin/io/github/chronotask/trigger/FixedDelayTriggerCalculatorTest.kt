package io.github.chronotask.trigger

import io.github.chronotask.model.TaskSchedule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class FixedDelayTriggerCalculatorTest {

    @Test
    fun `first trigger uses registeredAt plus initialDelay`() {
        val registered = Instant.parse("2026-01-01T10:00:00Z")
        val schedule = TaskSchedule.FixedDelay(delayMs = 5000L, initialDelayMs = 2000L)
        val calculator = FixedDelayTriggerCalculator(schedule)

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
    fun `subsequent trigger is strictly based on lastCompletedAt plus delay`() {
        val registered = Instant.parse("2026-01-01T10:00:00Z")
        val schedule = TaskSchedule.FixedDelay(delayMs = 5000L, initialDelayMs = 2000L)
        val calculator = FixedDelayTriggerCalculator(schedule)

        val completed = Instant.parse("2026-01-01T10:00:05Z")
        val context = TriggerContext(
            now = completed,
            registeredAt = registered,
            lastScheduledAt = Instant.parse("2026-01-01T10:00:02Z"),
            lastStartedAt = Instant.parse("2026-01-01T10:00:02Z"),
            lastCompletedAt = completed
        )

        val next = calculator.nextTrigger(context)
        // 10:00:05 + 5s = 10:00:10
        assertEquals(Instant.parse("2026-01-01T10:00:10Z"), next)
    }

    @Test
    fun `lastScheduledAt does not affect FixedDelay calculation`() {
        val registered = Instant.parse("2026-01-01T10:00:00Z")
        val schedule = TaskSchedule.FixedDelay(delayMs = 3000L, initialDelayMs = 0L)
        val calculator = FixedDelayTriggerCalculator(schedule)

        val completed = Instant.parse("2026-01-01T10:00:20Z")
        val context = TriggerContext(
            now = completed,
            registeredAt = registered,
            lastScheduledAt = Instant.parse("2026-01-01T10:00:00Z"), // Irrelevant
            lastStartedAt = Instant.parse("2026-01-01T10:00:15Z"),  // Irrelevant
            lastCompletedAt = completed
        )

        val next = calculator.nextTrigger(context)
        assertEquals(Instant.parse("2026-01-01T10:00:23Z"), next)
    }
}

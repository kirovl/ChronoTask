package io.github.chronotask.trigger

import io.github.chronotask.model.TaskSchedule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

class OnceTriggerCalculatorTest {

    @Test
    fun `delay zero triggers at registeredAt`() {
        val registered = Instant.parse("2026-01-01T10:00:00Z")
        val schedule = TaskSchedule.Once(delayMs = 0L)
        val calculator = OnceTriggerCalculator(schedule)

        val context = TriggerContext(
            now = registered,
            registeredAt = registered,
            lastScheduledAt = null,
            lastStartedAt = null,
            lastCompletedAt = null
        )

        assertEquals(registered, calculator.nextTrigger(context))
    }

    @Test
    fun `delay 5s triggers at registeredAt plus 5s`() {
        val registered = Instant.parse("2026-01-01T10:00:00Z")
        val schedule = TaskSchedule.Once(delayMs = 5000L)
        val calculator = OnceTriggerCalculator(schedule)

        val context = TriggerContext(
            now = registered,
            registeredAt = registered,
            lastScheduledAt = null,
            lastStartedAt = null,
            lastCompletedAt = null
        )

        assertEquals(Instant.parse("2026-01-01T10:00:05Z"), calculator.nextTrigger(context))
    }

    @Test
    fun `returns null after first scheduled execution`() {
        val registered = Instant.parse("2026-01-01T10:00:00Z")
        val schedule = TaskSchedule.Once(delayMs = 5000L)
        val calculator = OnceTriggerCalculator(schedule)

        val context = TriggerContext(
            now = Instant.parse("2026-01-01T10:00:06Z"),
            registeredAt = registered,
            lastScheduledAt = Instant.parse("2026-01-01T10:00:05Z"),
            lastStartedAt = Instant.parse("2026-01-01T10:00:05Z"),
            lastCompletedAt = Instant.parse("2026-01-01T10:00:06Z")
        )

        assertNull(calculator.nextTrigger(context))
    }
}

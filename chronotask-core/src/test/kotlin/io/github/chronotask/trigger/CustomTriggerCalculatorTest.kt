package io.github.chronotask.trigger

import io.github.chronotask.model.TaskSchedule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant

class CustomTriggerCalculatorTest {

    @Test
    fun `delegates to custom calculator`() {
        val target = Instant.parse("2026-10-01T08:00:00Z")
        val schedule = TaskSchedule.Custom { target }
        val calculator = CustomTriggerCalculator(schedule)

        val context = TriggerContext(
            now = Instant.parse("2026-01-01T00:00:00Z"),
            registeredAt = Instant.parse("2026-01-01T00:00:00Z"),
            lastScheduledAt = null,
            lastStartedAt = null,
            lastCompletedAt = null
        )

        assertEquals(target, calculator.nextTrigger(context))
    }

    @Test
    fun `custom calculator returns null for termination`() {
        val schedule = TaskSchedule.Custom { null }
        val calculator = CustomTriggerCalculator(schedule)

        val context = TriggerContext(
            now = Instant.parse("2026-01-01T00:00:00Z"),
            registeredAt = Instant.parse("2026-01-01T00:00:00Z"),
            lastScheduledAt = null,
            lastStartedAt = null,
            lastCompletedAt = null
        )

        assertNull(calculator.nextTrigger(context))
    }

    @Test
    fun `custom calculator exception is propagated without suppression`() {
        val schedule = TaskSchedule.Custom {
            throw IllegalStateException("Custom calculator failure")
        }
        val calculator = CustomTriggerCalculator(schedule)

        val context = TriggerContext(
            now = Instant.parse("2026-01-01T00:00:00Z"),
            registeredAt = Instant.parse("2026-01-01T00:00:00Z"),
            lastScheduledAt = null,
            lastStartedAt = null,
            lastCompletedAt = null
        )

        assertThrows(IllegalStateException::class.java) {
            calculator.nextTrigger(context)
        }
    }
}

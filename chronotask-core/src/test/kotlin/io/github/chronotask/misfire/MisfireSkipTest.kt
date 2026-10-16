package io.github.chronotask.misfire

import io.github.chronotask.model.MisfirePolicy
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.trigger.TriggerContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class MisfireSkipTest {

    @Test
    fun `Skip policy produces zero triggers and advances timeline to next future point`() {
        val registered = Instant.parse("2026-01-01T10:00:00Z")
        val now = Instant.parse("2026-01-01T10:03:30Z")
        val schedule = TaskSchedule.FixedRate(intervalMs = 60000L) // 1 minute interval

        val triggerCtx = TriggerContext(
            now = now,
            registeredAt = registered,
            lastScheduledAt = null,
            lastStartedAt = null,
            lastCompletedAt = null
        )

        val misfireCtx = MisfireContext(
            schedule = schedule,
            misfirePolicy = MisfirePolicy.SKIP,
            triggerContext = triggerCtx,
            firstScheduledAt = registered,
            now = now
        )

        val result = DefaultMisfireResolver.resolve(misfireCtx)

        assertTrue(result.triggers.isEmpty(), "Skip must not generate compensatory triggers")
        // 10:00, 10:01, 10:02, 10:03 missed -> next future is 10:04:00
        assertEquals(Instant.parse("2026-01-01T10:04:00Z"), result.nextScheduledAt)
    }

    @Test
    fun `Subsequent check after Skip does not repeat past missed triggers`() {
        val registered = Instant.parse("2026-01-01T10:00:00Z")
        val now = Instant.parse("2026-01-01T10:03:30Z")
        val schedule = TaskSchedule.FixedRate(intervalMs = 60000L)

        val triggerCtx = TriggerContext(
            now = now,
            registeredAt = registered,
            lastScheduledAt = Instant.parse("2026-01-01T10:04:00Z"), // Advanced timeline
            lastStartedAt = null,
            lastCompletedAt = null
        )

        // Next planned is now 10:05:00
        val calculator = io.github.chronotask.trigger.TriggerCalculatorFactory.create(schedule)
        val next = calculator.nextTrigger(triggerCtx)
        assertEquals(Instant.parse("2026-01-01T10:05:00Z"), next)
    }
}

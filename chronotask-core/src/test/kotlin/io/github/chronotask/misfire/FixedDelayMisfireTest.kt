package io.github.chronotask.misfire

import io.github.chronotask.model.MisfirePolicy
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.trigger.TriggerContext
import io.github.chronotask.trigger.TriggerSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class FixedDelayMisfireTest {

    @Test
    fun `FixedDelay CatchUp with large maxExecutions generates at most 1 trigger`() {
        val registered = Instant.parse("2026-01-01T10:00:00Z")
        val completed = Instant.parse("2026-01-01T10:00:00Z")
        val now = Instant.parse("2026-01-01T12:00:00Z") // 2 hours later
        val schedule = TaskSchedule.FixedDelay(delayMs = 300000L) // 5 minutes delay

        val triggerCtx = TriggerContext(
            now = now,
            registeredAt = registered,
            lastScheduledAt = null,
            lastStartedAt = null,
            lastCompletedAt = completed
        )

        val misfireCtx = MisfireContext(
            schedule = schedule,
            misfirePolicy = MisfirePolicy.CatchUp(maxExecutions = 100),
            triggerContext = triggerCtx,
            firstScheduledAt = Instant.parse("2026-01-01T10:05:00Z"),
            now = now
        )

        val result = DefaultMisfireResolver.resolve(misfireCtx)

        // Must generate exactly 1 trigger because subsequent triggers cannot be determined without completion
        assertEquals(1, result.triggers.size)
        assertEquals(Instant.parse("2026-01-01T10:05:00Z"), result.triggers[0].scheduledAt)
        assertEquals(TriggerSource.MISFIRE, result.triggers[0].source)
        assertNull(result.nextScheduledAt, "nextScheduledAt must be null until execution completes")
    }

    @Test
    fun `FixedDelay Skip produces zero triggers and reanchors the next delay`() {
        val registered = Instant.parse("2026-01-01T10:00:00Z")
        val now = Instant.parse("2026-01-01T12:00:00Z")
        val schedule = TaskSchedule.FixedDelay(delayMs = 300000L)

        val triggerCtx = TriggerContext(
            now = now,
            registeredAt = registered,
            lastScheduledAt = null,
            lastStartedAt = null,
            lastCompletedAt = registered
        )

        val misfireCtx = MisfireContext(
            schedule = schedule,
            misfirePolicy = MisfirePolicy.SKIP,
            triggerContext = triggerCtx,
            firstScheduledAt = Instant.parse("2026-01-01T10:05:00Z"),
            now = now
        )

        val result = DefaultMisfireResolver.resolve(misfireCtx)

        assertTrue(result.triggers.isEmpty())
        assertEquals(now.plusMillis(schedule.delayMs), result.nextScheduledAt)
        assertEquals(now, result.alignedLastScheduledAt)
    }
}

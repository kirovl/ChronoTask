package io.github.chronotask.misfire

import io.github.chronotask.model.MisfirePolicy
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.trigger.TriggerContext
import io.github.chronotask.trigger.TriggerSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class MisfireCatchUpTest {

    @Test
    fun `CatchUp limits triggers to maxExecutions and orders them in ascending order`() {
        val registered = Instant.parse("2026-01-01T10:00:00Z")
        val now = Instant.parse("2026-01-01T10:03:30Z") // 10:00, 10:01, 10:02, 10:03 missed (4 total)
        val schedule = TaskSchedule.FixedRate(intervalMs = 60000L)

        val triggerCtx = TriggerContext(
            now = now,
            registeredAt = registered,
            lastScheduledAt = null,
            lastStartedAt = null,
            lastCompletedAt = null
        )

        val misfireCtx = MisfireContext(
            schedule = schedule,
            misfirePolicy = MisfirePolicy.CatchUp(maxExecutions = 3),
            triggerContext = triggerCtx,
            firstScheduledAt = registered,
            now = now
        )

        val result = DefaultMisfireResolver.resolve(misfireCtx)

        assertEquals(3, result.triggers.size)
        // Strictly ascending
        assertEquals(Instant.parse("2026-01-01T10:00:00Z"), result.triggers[0].scheduledAt)
        assertEquals(Instant.parse("2026-01-01T10:01:00Z"), result.triggers[1].scheduledAt)
        assertEquals(Instant.parse("2026-01-01T10:02:00Z"), result.triggers[2].scheduledAt)

        for (t in result.triggers) {
            assertEquals(TriggerSource.MISFIRE, t.source)
            assertEquals(now, t.discoveredAt)
        }

        // Remaining missed (10:03) is truncated, timeline advances to next future (10:04)
        assertEquals(Instant.parse("2026-01-01T10:04:00Z"), result.nextScheduledAt)
    }

    @Test
    fun `CatchUp when missed count is less than maxExecutions returns all missed triggers`() {
        val registered = Instant.parse("2026-01-01T10:00:00Z")
        val now = Instant.parse("2026-01-01T10:02:30Z") // 10:00, 10:01, 10:02 missed (3 total)
        val schedule = TaskSchedule.FixedRate(intervalMs = 60000L)

        val triggerCtx = TriggerContext(
            now = now,
            registeredAt = registered,
            lastScheduledAt = null,
            lastStartedAt = null,
            lastCompletedAt = null
        )

        val misfireCtx = MisfireContext(
            schedule = schedule,
            misfirePolicy = MisfirePolicy.CatchUp(maxExecutions = 10),
            triggerContext = triggerCtx,
            firstScheduledAt = registered,
            now = now
        )

        val result = DefaultMisfireResolver.resolve(misfireCtx)

        assertEquals(3, result.triggers.size)
        assertEquals(Instant.parse("2026-01-01T10:00:00Z"), result.triggers[0].scheduledAt)
        assertEquals(Instant.parse("2026-01-01T10:01:00Z"), result.triggers[1].scheduledAt)
        assertEquals(Instant.parse("2026-01-01T10:02:00Z"), result.triggers[2].scheduledAt)
        assertEquals(Instant.parse("2026-01-01T10:03:00Z"), result.nextScheduledAt)
    }
}

package io.github.chronotask.engine.stress

import io.github.chronotask.misfire.DefaultMisfireResolver
import io.github.chronotask.misfire.MisfireContext
import io.github.chronotask.model.MisfirePolicy
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.trigger.TriggerContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class MisfireStressTest {

    @Test
    fun testLongVirtualTimeSkipMisfire() {
        val registered = Instant.parse("2026-01-01T00:00:00Z")
        val now = Instant.parse("2027-01-01T00:00:00.500Z") // 365 days later
        val schedule = TaskSchedule.FixedRate(1000L)

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
        assertTrue(result.triggers.isEmpty(), "SKIP policy should produce 0 compensatory triggers for 365-day misfire")
        assertEquals(Instant.parse("2027-01-01T00:00:01Z"), result.nextScheduledAt)
    }

    @Test
    fun testLongVirtualTimeFireOnceMisfire() {
        val registered = Instant.parse("2026-01-01T00:00:00Z")
        val now = Instant.parse("2026-01-31T00:00:00.500Z") // 30 days later
        val schedule = TaskSchedule.FixedRate(1000L)

        val triggerCtx = TriggerContext(
            now = now,
            registeredAt = registered,
            lastScheduledAt = null,
            lastStartedAt = null,
            lastCompletedAt = null
        )

        val misfireCtx = MisfireContext(
            schedule = schedule,
            misfirePolicy = MisfirePolicy.FIRE_ONCE,
            triggerContext = triggerCtx,
            firstScheduledAt = registered,
            now = now
        )

        val result = DefaultMisfireResolver.resolve(misfireCtx)
        assertEquals(1, result.triggers.size, "FIRE_ONCE should produce exactly 1 compensatory trigger")
        assertEquals(Instant.parse("2026-01-31T00:00:01Z"), result.nextScheduledAt)
    }

    @Test
    fun testBoundedCatchUpMisfireStress() {
        val registered = Instant.parse("2026-01-01T00:00:00Z")
        val now = Instant.parse("2026-01-02T00:00:00.500Z") // 1 day later (86,400 missed intervals)
        val schedule = TaskSchedule.FixedRate(1000L)

        val triggerCtx = TriggerContext(
            now = now,
            registeredAt = registered,
            lastScheduledAt = null,
            lastStartedAt = null,
            lastCompletedAt = null
        )

        val misfireCtx = MisfireContext(
            schedule = schedule,
            misfirePolicy = MisfirePolicy.CatchUp(maxExecutions = 5),
            triggerContext = triggerCtx,
            firstScheduledAt = registered,
            now = now
        )

        val result = DefaultMisfireResolver.resolve(misfireCtx)
        assertEquals(5, result.triggers.size, "CatchUp(5) must produce strictly 5 compensatory triggers")
        assertEquals(Instant.parse("2026-01-02T00:00:01Z"), result.nextScheduledAt)
    }
}

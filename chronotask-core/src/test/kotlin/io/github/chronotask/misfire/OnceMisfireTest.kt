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

class OnceMisfireTest {

    @Test
    fun `Once misfire with Skip produces empty triggers and null nextScheduledAt`() {
        val registered = Instant.parse("2026-01-01T10:00:00Z")
        val planned = Instant.parse("2026-01-01T10:05:00Z")
        val now = Instant.parse("2026-01-01T12:00:00Z")
        val schedule = TaskSchedule.Once(300000L)

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
            firstScheduledAt = planned,
            now = now
        )

        val result = DefaultMisfireResolver.resolve(misfireCtx)
        assertTrue(result.triggers.isEmpty())
        assertNull(result.nextScheduledAt)
    }

    @Test
    fun `Once misfire with FireOnce produces one trigger and null nextScheduledAt`() {
        val registered = Instant.parse("2026-01-01T10:00:00Z")
        val planned = Instant.parse("2026-01-01T10:05:00Z")
        val now = Instant.parse("2026-01-01T12:00:00Z")
        val schedule = TaskSchedule.Once(300000L)

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
            firstScheduledAt = planned,
            now = now
        )

        val result = DefaultMisfireResolver.resolve(misfireCtx)
        assertEquals(1, result.triggers.size)
        assertEquals(planned, result.triggers[0].scheduledAt)
        assertEquals(TriggerSource.MISFIRE, result.triggers[0].source)
        assertNull(result.nextScheduledAt)
    }

    @Test
    fun `Once misfire with CatchUp produces at most one trigger and null nextScheduledAt`() {
        val registered = Instant.parse("2026-01-01T10:00:00Z")
        val planned = Instant.parse("2026-01-01T10:05:00Z")
        val now = Instant.parse("2026-01-01T12:00:00Z")
        val schedule = TaskSchedule.Once(300000L)

        val triggerCtx = TriggerContext(
            now = now,
            registeredAt = registered,
            lastScheduledAt = null,
            lastStartedAt = null,
            lastCompletedAt = null
        )

        val misfireCtx = MisfireContext(
            schedule = schedule,
            misfirePolicy = MisfirePolicy.CatchUp(100),
            triggerContext = triggerCtx,
            firstScheduledAt = planned,
            now = now
        )

        val result = DefaultMisfireResolver.resolve(misfireCtx)
        assertEquals(1, result.triggers.size)
        assertEquals(planned, result.triggers[0].scheduledAt)
        assertNull(result.nextScheduledAt)
    }
}

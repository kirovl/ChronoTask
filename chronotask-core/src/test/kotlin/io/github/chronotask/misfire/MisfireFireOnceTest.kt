package io.github.chronotask.misfire

import io.github.chronotask.model.MisfirePolicy
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.trigger.TriggerContext
import io.github.chronotask.trigger.TriggerSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class MisfireFireOnceTest {

    @Test
    fun `FireOnce produces exactly one trigger using last missed schedule instant`() {
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
            misfirePolicy = MisfirePolicy.FIRE_ONCE,
            triggerContext = triggerCtx,
            firstScheduledAt = registered,
            now = now
        )

        val result = DefaultMisfireResolver.resolve(misfireCtx)

        assertEquals(1, result.triggers.size)
        val trigger = result.triggers[0]
        assertEquals(TriggerSource.MISFIRE, trigger.source)
        // Last missed is 10:03:00
        assertEquals(Instant.parse("2026-01-01T10:03:00Z"), trigger.scheduledAt)
        assertEquals(now, trigger.discoveredAt)
        // Next future point is 10:04:00
        assertEquals(Instant.parse("2026-01-01T10:04:00Z"), result.nextScheduledAt)
    }

    @Test
    fun `FireOnce with single missed trigger uses that exact missed instant`() {
        val registered = Instant.parse("2026-01-01T10:00:00Z")
        val now = Instant.parse("2026-01-01T10:00:05Z")
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
            misfirePolicy = MisfirePolicy.FIRE_ONCE,
            triggerContext = triggerCtx,
            firstScheduledAt = registered,
            now = now
        )

        val result = DefaultMisfireResolver.resolve(misfireCtx)

        assertEquals(1, result.triggers.size)
        assertEquals(registered, result.triggers[0].scheduledAt)
        assertEquals(Instant.parse("2026-01-01T10:01:00Z"), result.nextScheduledAt)
    }
}

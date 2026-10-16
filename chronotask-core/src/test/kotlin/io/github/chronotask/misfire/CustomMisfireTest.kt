package io.github.chronotask.misfire

import io.github.chronotask.model.MisfirePolicy
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.trigger.TriggerCalculationException
import io.github.chronotask.trigger.TriggerContext
import io.github.chronotask.trigger.TriggerSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant

class CustomMisfireTest {

    @Test
    fun `Custom misfire handles progression and advances to next future point`() {
        val registered = Instant.parse("2026-01-01T10:00:00Z")
        val now = Instant.parse("2026-01-01T10:15:00Z")

        // Custom schedule that triggers every 5 minutes from lastScheduledAt
        val schedule = TaskSchedule.Custom { ctx ->
            if (ctx.lastScheduledAt == null) ctx.registeredAt else ctx.lastScheduledAt.plusSeconds(300)
        }

        val triggerCtx = TriggerContext(
            now = now,
            registeredAt = registered,
            lastScheduledAt = null,
            lastStartedAt = null,
            lastCompletedAt = null
        )

        val misfireCtx = MisfireContext(
            schedule = schedule,
            misfirePolicy = MisfirePolicy.CatchUp(2),
            triggerContext = triggerCtx,
            firstScheduledAt = registered, // 10:00:00
            now = now
        )

        val result = DefaultMisfireResolver.resolve(misfireCtx)
        // Missed are: 10:00, 10:05, 10:10. CatchUp(2) takes 10:00, 10:05
        assertEquals(2, result.triggers.size)
        assertEquals(Instant.parse("2026-01-01T10:00:00Z"), result.triggers[0].scheduledAt)
        assertEquals(Instant.parse("2026-01-01T10:05:00Z"), result.triggers[1].scheduledAt)
        assertEquals(TriggerSource.MISFIRE, result.triggers[0].source)
        // Next future is 10:15:00 (since 10:15 >= now)
        assertEquals(Instant.parse("2026-01-01T10:15:00Z"), result.nextScheduledAt)
    }

    @Test
    fun `Custom non-progressing schedule throws TriggerCalculationException`() {
        val registered = Instant.parse("2026-01-01T10:00:00Z")
        val now = Instant.parse("2026-01-01T10:10:00Z")

        // Bad custom schedule returning constant time
        val badSchedule = TaskSchedule.Custom { registered }

        val triggerCtx = TriggerContext(
            now = now,
            registeredAt = registered,
            lastScheduledAt = null,
            lastStartedAt = null,
            lastCompletedAt = null
        )

        val misfireCtx = MisfireContext(
            schedule = badSchedule,
            misfirePolicy = MisfirePolicy.SKIP,
            triggerContext = triggerCtx,
            firstScheduledAt = registered,
            now = now
        )

        assertThrows(TriggerCalculationException::class.java) {
            DefaultMisfireResolver.resolve(misfireCtx)
        }
    }
}

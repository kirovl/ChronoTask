package io.github.chronotask.trigger

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant

class TriggerModelTest {

    @Test
    fun `test TriggerContext fields and semantics`() {
        val now = Instant.parse("2026-09-18T10:00:00Z")
        val reg = Instant.parse("2026-09-18T09:00:00Z")
        val scheduled = Instant.parse("2026-09-18T09:30:00Z")
        val started = Instant.parse("2026-09-18T09:30:02Z")
        val completed = Instant.parse("2026-09-18T09:30:05Z")

        val ctx = TriggerContext(
            now = now,
            registeredAt = reg,
            lastScheduledAt = scheduled,
            lastStartedAt = started,
            lastCompletedAt = completed
        )

        assertEquals(now, ctx.now)
        assertEquals(reg, ctx.registeredAt)
        assertEquals(scheduled, ctx.lastScheduledAt)
        assertEquals(started, ctx.lastStartedAt)
        assertEquals(completed, ctx.lastCompletedAt)

        val firstCtx = TriggerContext(now, reg, null, null, null)
        assertNull(firstCtx.lastScheduledAt)
        assertNull(firstCtx.lastStartedAt)
        assertNull(firstCtx.lastCompletedAt)
    }

    @Test
    fun `test ScheduledTrigger fields and defaults`() {
        val sched = Instant.parse("2026-09-18T10:00:00Z")
        val disc = Instant.parse("2026-09-18T10:00:08Z")

        val triggerDefault = ScheduledTrigger(sched, disc)
        assertEquals(sched, triggerDefault.scheduledAt)
        assertEquals(disc, triggerDefault.discoveredAt)
        assertEquals(TriggerSource.SCHEDULE, triggerDefault.source)

        val triggerMisfire = ScheduledTrigger(sched, disc, TriggerSource.MISFIRE)
        assertEquals(TriggerSource.MISFIRE, triggerMisfire.source)

        val triggerManual = ScheduledTrigger(sched, disc, TriggerSource.MANUAL)
        assertEquals(TriggerSource.MANUAL, triggerManual.source)
    }

    @Test
    fun `test TriggerCalculator SAM evaluation`() {
        val calc = TriggerCalculator { ctx -> ctx.now.plusSeconds(30) }
        val now = Instant.now()
        val ctx = TriggerContext(now, now, null, null, null)
        val next = calc.nextTrigger(ctx)

        assertNotNull(next)
        assertEquals(now.plusSeconds(30), next)
    }
}

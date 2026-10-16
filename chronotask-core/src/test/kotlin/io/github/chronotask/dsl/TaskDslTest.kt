package io.github.chronotask.dsl

import io.github.chronotask.cron.CronDialect
import io.github.chronotask.model.ConcurrencyPolicy
import io.github.chronotask.model.MisfirePolicy
import io.github.chronotask.model.TaskSchedule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class TaskDslTest {

    @Test
    fun `configures fixedRate task with custom properties`() {
        val dsl = TaskDsl("cleanup").apply {
            name = "Expired Cache Cleanup"
            fixedRate(interval = 5.minutes, initialDelay = 10.seconds)
            concurrency = ConcurrencyPolicy.Allow
            misfire = MisfirePolicy.FireOnce
            dispatcherKey = "custom-pool"
            enabled = false
            execute { /* no-op */ }
        }

        val def = dsl.build()
        assertEquals("cleanup", def.id)
        assertEquals("Expired Cache Cleanup", def.name)
        val schedule = assertInstanceOf(TaskSchedule.FixedRate::class.java, def.schedule)
        assertEquals(300000L, schedule.intervalMs)
        assertEquals(10000L, schedule.initialDelayMs)
        assertEquals(ConcurrencyPolicy.Allow, def.concurrencyPolicy)
        assertEquals(MisfirePolicy.FireOnce, def.misfirePolicy)
        assertEquals("custom-pool", def.dispatcherKey)
        assertEquals(false, def.isEnabled)
    }

    @Test
    fun `configures fixedDelay task with default properties`() {
        val dsl = TaskDsl("sync").apply {
            fixedDelay(delay = 30.seconds)
            execute { /* no-op */ }
        }

        val def = dsl.build()
        assertEquals("sync", def.id)
        assertEquals("sync", def.name)
        val schedule = assertInstanceOf(TaskSchedule.FixedDelay::class.java, def.schedule)
        assertEquals(30000L, schedule.delayMs)
        assertEquals(0L, schedule.initialDelayMs)
        assertEquals(ConcurrencyPolicy.SkipIfRunning, def.concurrencyPolicy)
        assertEquals(MisfirePolicy.Skip, def.misfirePolicy)
        assertEquals(null, def.dispatcherKey)
        assertTrue(def.isEnabled)
    }

    @Test
    fun `configures once task`() {
        val dsl = TaskDsl("init-task").apply {
            once(delay = 5.seconds)
            execute { /* no-op */ }
        }

        val def = dsl.build()
        val schedule = assertInstanceOf(TaskSchedule.Once::class.java, def.schedule)
        assertEquals(5000L, schedule.delayMs)
    }

    @Test
    fun `configures cron task`() {
        val zone = ZoneId.of("Asia/Singapore")
        val dsl = TaskDsl("cron-report").apply {
            cron("0 0 9 ? * MON-FRI", zone, CronDialect.QUARTZ)
            execute { /* no-op */ }
        }

        val def = dsl.build()
        val schedule = assertInstanceOf(TaskSchedule.Cron::class.java, def.schedule)
        assertEquals("0 0 9 ? * MON-FRI", schedule.expression)
        assertEquals(zone, schedule.zoneId)
        assertEquals(CronDialect.QUARTZ, schedule.dialect)
    }

    @Test
    fun `configures custom schedule task`() {
        val fixedInstant = Instant.parse("2026-10-01T12:00:00Z")
        val dsl = TaskDsl("custom-task").apply {
            custom { _ -> fixedInstant }
            execute { /* no-op */ }
        }

        val def = dsl.build()
        val schedule = assertInstanceOf(TaskSchedule.Custom::class.java, def.schedule)
        assertNotNull(schedule.calculator)
    }
}

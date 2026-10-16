package io.github.chronotask.model

import io.github.chronotask.cron.CronDialect
import io.github.chronotask.trigger.TriggerCalculator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class TaskScheduleTest {

    @Test
    fun `TimeUnit constructors reject negative delays before truncation`() {
        for (unit in TimeUnit.values()) {
            assertThrows<IllegalArgumentException> { TaskSchedule.Once(-1L, unit) }
            assertThrows<IllegalArgumentException> {
                TaskSchedule.FixedRate(1L, TimeUnit.SECONDS, -1L, unit)
            }
            assertThrows<IllegalArgumentException> {
                TaskSchedule.FixedDelay(1L, TimeUnit.SECONDS, -1L, unit)
            }
        }
    }

    @Test
    fun `TimeUnit constructors preserve zero delays and positive conversions`() {
        for (unit in TimeUnit.values()) {
            assertEquals(0L, TaskSchedule.Once(0L, unit).delayMs)
            assertEquals(0L, TaskSchedule.FixedRate(1L, TimeUnit.SECONDS, 0L, unit).initialDelayMs)
            assertEquals(0L, TaskSchedule.FixedDelay(1L, TimeUnit.SECONDS, 0L, unit).initialDelayMs)
        }
        assertEquals(2L, TaskSchedule.Once(2000L, TimeUnit.MICROSECONDS).delayMs)
        assertEquals(3L, TaskSchedule.FixedRate(1L, TimeUnit.SECONDS, 3_000_000L, TimeUnit.NANOSECONDS).initialDelayMs)
        assertEquals(4L, TaskSchedule.FixedDelay(1L, TimeUnit.SECONDS, 4000L, TimeUnit.MICROSECONDS).initialDelayMs)
    }

    @Test
    fun `test FixedRate validation`() {
        assertThrows<IllegalArgumentException> { TaskSchedule.FixedRate(0L) }
        assertThrows<IllegalArgumentException> { TaskSchedule.FixedRate(-1L) }
        assertThrows<IllegalArgumentException> { TaskSchedule.FixedRate(1000L, -1L) }

        val schedule1 = TaskSchedule.FixedRate(5000L)
        assertEquals(5000L, schedule1.intervalMs)
        assertEquals(0L, schedule1.initialDelayMs)

        val schedule2 = TaskSchedule.FixedRate(10, TimeUnit.SECONDS, 2, TimeUnit.SECONDS)
        assertEquals(10000L, schedule2.intervalMs)
        assertEquals(2000L, schedule2.initialDelayMs)

        assertEquals(schedule1, TaskSchedule.FixedRate(5000L, 0L))
    }

    @Test
    fun `test FixedDelay validation`() {
        assertThrows<IllegalArgumentException> { TaskSchedule.FixedDelay(0L) }
        assertThrows<IllegalArgumentException> { TaskSchedule.FixedDelay(-1L) }
        assertThrows<IllegalArgumentException> { TaskSchedule.FixedDelay(1000L, -1L) }

        val schedule1 = TaskSchedule.FixedDelay(3000L)
        assertEquals(3000L, schedule1.delayMs)
        assertEquals(0L, schedule1.initialDelayMs)

        val schedule2 = TaskSchedule.FixedDelay(5, TimeUnit.MINUTES, 1, TimeUnit.MINUTES)
        assertEquals(300000L, schedule2.delayMs)
        assertEquals(60000L, schedule2.initialDelayMs)

        assertEquals(schedule1, TaskSchedule.FixedDelay(3000L, 0L))
    }

    @Test
    fun `test Cron validation`() {
        val zone = ZoneId.of("Asia/Shanghai")
        assertThrows<IllegalArgumentException> { TaskSchedule.Cron("", zone) }
        assertThrows<IllegalArgumentException> { TaskSchedule.Cron("   ", zone) }

        // Cron expression parsing is deferred to M6, M1 allows construction of any non-blank string
        val cron1 = TaskSchedule.Cron("0 0 12 * * ?", zone)
        assertEquals("0 0 12 * * ?", cron1.expression)
        assertEquals(zone, cron1.zoneId)
        assertEquals(CronDialect.QUARTZ, cron1.dialect)

        val cron2 = TaskSchedule.Cron("0 * * * *", ZoneId.of("UTC"), CronDialect.UNIX)
        assertEquals(CronDialect.UNIX, cron2.dialect)

        assertEquals(cron1, TaskSchedule.Cron("0 0 12 * * ?", zone, CronDialect.QUARTZ))
    }

    @Test
    fun `test Once validation`() {
        assertThrows<IllegalArgumentException> { TaskSchedule.Once(-1L) }

        val once0 = TaskSchedule.Once(0L)
        assertEquals(0L, once0.delayMs)

        val once1000 = TaskSchedule.Once(1000L)
        assertEquals(1000L, once1000.delayMs)

        val onceUnit = TaskSchedule.Once(2, TimeUnit.SECONDS)
        assertEquals(2000L, onceUnit.delayMs)

        assertEquals(once1000, TaskSchedule.Once(1000L))
    }

    @Test
    fun `test Custom schedule with TriggerCalculator`() {
        val calc = TriggerCalculator { ctx -> ctx.now.plusSeconds(10) }
        val custom = TaskSchedule.Custom(calc)
        assertSame(calc, custom.calculator)
    }
}

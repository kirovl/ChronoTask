package io.github.chronotask.job

import io.github.chronotask.cron.CronDialect
import io.github.chronotask.model.TaskSchedule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZoneId

class ManagedCronJobTest {

    private class DailyReportJob : ManagedCronJob(
        id = "daily-report",
        cron = "0 0 12 * * ?",
        zoneId = ZoneId.of("Asia/Shanghai")
    ) {
        override suspend fun execute(context: TaskExecutionContext) {}
    }

    private class UnixCronJob : ManagedCronJob(
        id = "unix-cron",
        cron = "*/5 * * * *",
        zoneId = ZoneId.of("UTC"),
        dialect = CronDialect.UNIX
    ) {
        override suspend fun execute(context: TaskExecutionContext) {}
    }

    @Test
    fun `maps quartz managed cron job to TaskDefinition`() {
        val job = DailyReportJob()
        val def = job.toTaskDefinition()

        assertEquals("daily-report", def.id)
        assertTrue(def.schedule is TaskSchedule.Cron)
        val schedule = def.schedule as TaskSchedule.Cron
        assertEquals("0 0 12 * * ?", schedule.expression)
        assertEquals(ZoneId.of("Asia/Shanghai"), schedule.zoneId)
        assertEquals(CronDialect.QUARTZ, schedule.dialect)
    }

    @Test
    fun `maps unix managed cron job to TaskDefinition`() {
        val job = UnixCronJob()
        val def = job.toTaskDefinition()

        assertEquals("unix-cron", def.id)
        assertTrue(def.schedule is TaskSchedule.Cron)
        val schedule = def.schedule as TaskSchedule.Cron
        assertEquals("*/5 * * * *", schedule.expression)
        assertEquals(ZoneId.of("UTC"), schedule.zoneId)
        assertEquals(CronDialect.UNIX, schedule.dialect)
    }
}

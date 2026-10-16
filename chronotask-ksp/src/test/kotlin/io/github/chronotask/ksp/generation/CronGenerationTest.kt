package io.github.chronotask.ksp.generation

import io.github.chronotask.annotation.Cron
import io.github.chronotask.cron.CronDialect
import io.github.chronotask.model.TaskSchedule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZoneId

class CronTarget {
    @Cron(
        expression = "0 0 12 * * ?",
        zone = "Asia/Tokyo",
        dialect = CronDialect.QUARTZ,
        id = "noon-cron-task"
    )
    fun noonTask() {}

    @Cron(
        expression = "0 12 * * *",
        zone = "UTC",
        dialect = CronDialect.UNIX,
        id = "unix-cron-task"
    )
    fun unixTask() {}
}

class CronGenerationTest {

    @Test
    fun `definitions accurately maps Cron properties for Quartz and UNIX dialects`() {
        val target = CronTarget()
        val definitions = CronTarget_ChronoTaskBinding.definitions(target)

        assertEquals(2, definitions.size)

        val noonDef = definitions.first { it.id == "noon-cron-task" }
        assertTrue(noonDef.schedule is TaskSchedule.Cron)
        val noonSchedule = noonDef.schedule as TaskSchedule.Cron
        assertEquals("0 0 12 * * ?", noonSchedule.expression)
        assertEquals(ZoneId.of("Asia/Tokyo"), noonSchedule.zoneId)
        assertEquals(CronDialect.QUARTZ, noonSchedule.dialect)

        val unixDef = definitions.first { it.id == "unix-cron-task" }
        assertTrue(unixDef.schedule is TaskSchedule.Cron)
        val unixSchedule = unixDef.schedule as TaskSchedule.Cron
        assertEquals("0 12 * * *", unixSchedule.expression)
        assertEquals(ZoneId.of("UTC"), unixSchedule.zoneId)
        assertEquals(CronDialect.UNIX, unixSchedule.dialect)
    }
}

package io.github.chronotask.builder

import io.github.chronotask.ChronoTask
import io.github.chronotask.cron.CronDialect
import io.github.chronotask.model.ConcurrencyPolicy
import io.github.chronotask.model.MisfirePolicy
import io.github.chronotask.model.QueueOverflowPolicy
import io.github.chronotask.model.TaskSchedule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.ZoneId

class TaskBuilderTest {

    @Test
    fun `builds TaskDefinition with full configuration`() {
        val def = ChronoTask.newTask("builder-task")
            .name("Builder Test Task")
            .fixedRate(Duration.ofMinutes(1), Duration.ofSeconds(5))
            .serial(20, QueueOverflowPolicy.DROP_LATEST)
            .onMisfireCatchUp(5)
            .dispatcher("custom-dispatcher")
            .enabled(true)
            .executeRunnable { /* no-op */ }
            .build()

        assertEquals("builder-task", def.id)
        assertEquals("Builder Test Task", def.name)
        val schedule = assertInstanceOf(TaskSchedule.FixedRate::class.java, def.schedule)
        assertEquals(60000L, schedule.intervalMs)
        assertEquals(5000L, schedule.initialDelayMs)

        val concurrency = assertInstanceOf(ConcurrencyPolicy.Serial::class.java, def.concurrencyPolicy)
        assertEquals(20, concurrency.maxQueueSize)
        assertEquals(QueueOverflowPolicy.DROP_LATEST, concurrency.overflowPolicy)

        val misfire = assertInstanceOf(MisfirePolicy.CatchUp::class.java, def.misfirePolicy)
        assertEquals(5, misfire.maxExecutions)

        assertEquals("custom-dispatcher", def.dispatcherKey)
        assertTrue(def.isEnabled)
    }

    @Test
    fun `builder is reusable and produces independent definitions`() {
        val builder = ChronoTask.newTask("reusable-task")
            .fixedDelay(Duration.ofSeconds(10))
            .executeRunnable { /* no-op */ }

        val def1 = builder.build()
        assertEquals("reusable-task", def1.name)

        builder.name("Modified Name")
        val def2 = builder.build()

        assertEquals("reusable-task", def1.name)
        assertEquals("Modified Name", def2.name)
    }

    @Test
    fun `builds cron task with unix dialect`() {
        val zone = ZoneId.of("UTC")
        val def = ChronoTask.newTask("cron-unix")
            .cron("*/5 * * * *", zone, CronDialect.UNIX)
            .executeRunnable { /* no-op */ }
            .build()

        val schedule = assertInstanceOf(TaskSchedule.Cron::class.java, def.schedule)
        assertEquals("*/5 * * * *", schedule.expression)
        assertEquals(zone, schedule.zoneId)
        assertEquals(CronDialect.UNIX, schedule.dialect)
    }
}

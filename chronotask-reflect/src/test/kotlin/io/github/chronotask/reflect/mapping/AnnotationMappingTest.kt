package io.github.chronotask.reflect.mapping

import io.github.chronotask.annotation.AnnotationConcurrency
import io.github.chronotask.annotation.AnnotationMisfire
import io.github.chronotask.annotation.Cron
import io.github.chronotask.annotation.Once
import io.github.chronotask.annotation.ScheduleMode
import io.github.chronotask.annotation.Scheduled
import io.github.chronotask.annotation.TickTask
import io.github.chronotask.cron.CronDialect
import io.github.chronotask.model.ConcurrencyPolicy
import io.github.chronotask.model.MisfirePolicy
import io.github.chronotask.model.QueueOverflowPolicy
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.reflect.ReflectionTaskScanner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZoneId

class AnnotationMappingTest {

    class MappingService {
        @Scheduled(intervalMs = 60000, initialDelayMs = 5000, mode = ScheduleMode.FIXED_RATE, id = "rate-task")
        fun rate() {}

        @Scheduled(intervalMs = 30000, initialDelayMs = 1000, mode = ScheduleMode.FIXED_DELAY, id = "delay-task")
        fun delay() {}

        @Cron(expression = "0 0 10 * * ?", zone = "Asia/Tokyo", dialect = CronDialect.QUARTZ, id = "cron-task")
        fun cron() {}

        @Once(delayMs = 15000, id = "once-task")
        fun once() {}

        @TickTask(intervalMs = 500, initialDelayMs = 100, id = "tick-task")
        fun tick() {}

        @Scheduled(
            intervalMs = 1000,
            id = "policy-task",
            concurrency = AnnotationConcurrency.ALLOW,
            misfire = AnnotationMisfire.FIRE_ONCE,
            dispatcher = "custom-pool",
            enabled = false
        )
        fun policy() {}

        @Scheduled(
            intervalMs = 1000,
            id = "serial-task",
            concurrency = AnnotationConcurrency.SERIAL,
            serialQueueSize = 50,
            serialOverflow = QueueOverflowPolicy.REJECT
        )
        fun serial() {}

        @Scheduled(
            intervalMs = 1000,
            id = "catchup-task",
            misfire = AnnotationMisfire.CATCH_UP,
            catchUpLimit = 30
        )
        fun catchUp() {}
    }

    @Test
    fun `verifies all annotation mappings against frozen domain models`() {
        val defs = ReflectionTaskScanner.scan(MappingService()).associateBy { it.id }

        // FixedRate
        val rate = defs["rate-task"]!!
        assertEquals(TaskSchedule.FixedRate(60000L, 5000L), rate.schedule)
        assertEquals(ConcurrencyPolicy.SkipIfRunning, rate.concurrencyPolicy)
        assertEquals(MisfirePolicy.Skip, rate.misfirePolicy)
        assertNull(rate.dispatcherKey)
        assertTrue(rate.isEnabled)

        // FixedDelay
        val delay = defs["delay-task"]!!
        assertEquals(TaskSchedule.FixedDelay(30000L, 1000L), delay.schedule)

        // Cron
        val cron = defs["cron-task"]!!
        assertEquals(TaskSchedule.Cron("0 0 10 * * ?", ZoneId.of("Asia/Tokyo"), CronDialect.QUARTZ), cron.schedule)

        // Once
        val once = defs["once-task"]!!
        assertEquals(TaskSchedule.Once(15000L), once.schedule)

        // TickTask
        val tick = defs["tick-task"]!!
        assertEquals(TaskSchedule.FixedRate(500L, 100L), tick.schedule)

        // Policies
        val policy = defs["policy-task"]!!
        assertEquals(ConcurrencyPolicy.Allow, policy.concurrencyPolicy)
        assertEquals(MisfirePolicy.FIRE_ONCE, policy.misfirePolicy)
        assertEquals("custom-pool", policy.dispatcherKey)
        assertFalse(policy.isEnabled)

        // Serial
        val serial = defs["serial-task"]!!
        assertEquals(ConcurrencyPolicy.Serial(50, QueueOverflowPolicy.REJECT), serial.concurrencyPolicy)

        // CatchUp
        val catchUp = defs["catchup-task"]!!
        assertEquals(MisfirePolicy.CatchUp(30), catchUp.misfirePolicy)
    }
}

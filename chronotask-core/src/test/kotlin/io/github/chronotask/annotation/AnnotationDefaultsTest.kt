package io.github.chronotask.annotation

import io.github.chronotask.cron.CronDialect
import io.github.chronotask.model.QueueOverflowPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnnotationDefaultsTest {

    class SampleService {
        @Scheduled(intervalMs = 5000)
        fun scheduledMethod() {}

        @Cron(expression = "0 0 * * * ?")
        fun cronMethod() {}

        @Once
        fun onceMethod() {}

        @TickTask(intervalMs = 1000)
        fun tickMethod() {}
    }

    @Test
    fun `Scheduled default values match specification`() {
        val method = SampleService::class.java.getMethod("scheduledMethod")
        val scheduled = method.getAnnotation(Scheduled::class.java)

        assertEquals(5000L, scheduled.intervalMs)
        assertEquals(0L, scheduled.initialDelayMs)
        assertEquals(ScheduleMode.FIXED_RATE, scheduled.mode)
        assertEquals("", scheduled.id)
        assertEquals("", scheduled.name)
        assertEquals(AnnotationConcurrency.SKIP_IF_RUNNING, scheduled.concurrency)
        assertEquals(AnnotationMisfire.SKIP, scheduled.misfire)
        assertEquals(100, scheduled.catchUpLimit)
        assertEquals("", scheduled.dispatcher)
        assertTrue(scheduled.enabled)
        assertEquals(1024, scheduled.serialQueueSize)
        assertEquals(QueueOverflowPolicy.DROP_OLDEST, scheduled.serialOverflow)
    }

    @Test
    fun `Cron default values match specification`() {
        val method = SampleService::class.java.getMethod("cronMethod")
        val cron = method.getAnnotation(Cron::class.java)

        assertEquals("0 0 * * * ?", cron.expression)
        assertEquals("UTC", cron.zone)
        assertEquals(CronDialect.QUARTZ, cron.dialect)
        assertEquals("", cron.id)
        assertEquals("", cron.name)
        assertEquals(AnnotationConcurrency.SKIP_IF_RUNNING, cron.concurrency)
        assertEquals(AnnotationMisfire.SKIP, cron.misfire)
        assertEquals(100, cron.catchUpLimit)
        assertEquals("", cron.dispatcher)
        assertTrue(cron.enabled)
        assertEquals(1024, cron.serialQueueSize)
        assertEquals(QueueOverflowPolicy.DROP_OLDEST, cron.serialOverflow)
    }

    @Test
    fun `Once default values match specification`() {
        val method = SampleService::class.java.getMethod("onceMethod")
        val once = method.getAnnotation(Once::class.java)

        assertEquals(0L, once.delayMs)
        assertEquals("", once.id)
        assertEquals("", once.name)
        assertEquals(AnnotationConcurrency.SKIP_IF_RUNNING, once.concurrency)
        assertEquals(AnnotationMisfire.SKIP, once.misfire)
        assertEquals(100, once.catchUpLimit)
        assertEquals("", once.dispatcher)
        assertTrue(once.enabled)
        assertEquals(1024, once.serialQueueSize)
        assertEquals(QueueOverflowPolicy.DROP_OLDEST, once.serialOverflow)
    }

    @Test
    fun `TickTask default values match specification`() {
        val method = SampleService::class.java.getMethod("tickMethod")
        val tick = method.getAnnotation(TickTask::class.java)

        assertEquals(1000L, tick.intervalMs)
        assertEquals(0L, tick.initialDelayMs)
        assertEquals("", tick.id)
        assertEquals("", tick.name)
        assertEquals(AnnotationConcurrency.SKIP_IF_RUNNING, tick.concurrency)
        assertEquals(AnnotationMisfire.SKIP, tick.misfire)
        assertEquals(100, tick.catchUpLimit)
        assertEquals("", tick.dispatcher)
        assertTrue(tick.enabled)
        assertEquals(1024, tick.serialQueueSize)
        assertEquals(QueueOverflowPolicy.DROP_OLDEST, tick.serialOverflow)
    }
}

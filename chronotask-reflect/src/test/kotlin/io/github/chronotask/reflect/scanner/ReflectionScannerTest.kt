package io.github.chronotask.reflect.scanner

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

class ReflectionScannerTest {

    class CompleteSampleService {

        @Scheduled(intervalMs = 5000, initialDelayMs = 1000, mode = ScheduleMode.FIXED_RATE, id = "fixed-rate-task")
        fun taskFixedRate() {}

        @Scheduled(
            intervalMs = 10000,
            initialDelayMs = 2000,
            mode = ScheduleMode.FIXED_DELAY,
            id = "fixed-delay-task",
            name = "Fixed Delay Task",
            concurrency = AnnotationConcurrency.SERIAL,
            serialQueueSize = 256,
            serialOverflow = QueueOverflowPolicy.DROP_LATEST,
            misfire = AnnotationMisfire.CATCH_UP,
            catchUpLimit = 20,
            dispatcher = "io-pool",
            enabled = false
        )
        fun taskFixedDelay() {}

        @Cron(expression = "0 0 12 * * ?", zone = "Asia/Shanghai", dialect = CronDialect.QUARTZ, id = "cron-task")
        fun taskCron() {}

        @Once(delayMs = 3000, id = "once-task")
        fun taskOnce() {}

        @TickTask(intervalMs = 1000, initialDelayMs = 500, id = "tick-task")
        fun taskTick() {}

        @Scheduled(intervalMs = 2000)
        fun taskAutoId() {}

        fun unannotatedMethod() {}
    }

    class EmptyService {
        fun doSomething() {}
    }

    @Test
    fun `scans all annotated methods with exact definition mappings`() {
        val service = CompleteSampleService()
        val definitions = ReflectionTaskScanner.scan(service)

        // 6 annotated methods
        assertEquals(6, definitions.size)

        val byId = definitions.associateBy { it.id }

        // 1. FixedRate
        val d1 = byId["fixed-rate-task"]!!
        assertEquals("fixed-rate-task", d1.name)
        assertEquals(TaskSchedule.FixedRate(5000L, 1000L), d1.schedule)
        assertEquals(ConcurrencyPolicy.SkipIfRunning, d1.concurrencyPolicy)
        assertEquals(MisfirePolicy.Skip, d1.misfirePolicy)
        assertNull(d1.dispatcherKey)
        assertTrue(d1.isEnabled)

        // 2. FixedDelay full config
        val d2 = byId["fixed-delay-task"]!!
        assertEquals("Fixed Delay Task", d2.name)
        assertEquals(TaskSchedule.FixedDelay(10000L, 2000L), d2.schedule)
        assertEquals(ConcurrencyPolicy.Serial(256, QueueOverflowPolicy.DROP_LATEST), d2.concurrencyPolicy)
        assertEquals(MisfirePolicy.CatchUp(20), d2.misfirePolicy)
        assertEquals("io-pool", d2.dispatcherKey)
        assertFalse(d2.isEnabled)

        // 3. Cron
        val d3 = byId["cron-task"]!!
        assertEquals(TaskSchedule.Cron("0 0 12 * * ?", ZoneId.of("Asia/Shanghai"), CronDialect.QUARTZ), d3.schedule)

        // 4. Once
        val d4 = byId["once-task"]!!
        assertEquals(TaskSchedule.Once(3000L), d4.schedule)

        // 5. TickTask
        val d5 = byId["tick-task"]!!
        assertEquals(TaskSchedule.FixedRate(1000L, 500L), d5.schedule)

        // 6. Auto ID
        val autoId = "${CompleteSampleService::class.java.canonicalName}#taskAutoId"
        val d6 = byId[autoId]!!
        assertEquals(autoId, d6.name)
        assertEquals(TaskSchedule.FixedRate(2000L, 0L), d6.schedule)
    }

    @Test
    fun `empty service produces empty definitions list`() {
        val service = EmptyService()
        val definitions = ReflectionTaskScanner.scan(service)
        assertTrue(definitions.isEmpty())
    }
}

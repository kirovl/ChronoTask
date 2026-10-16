package io.github.chronotask.reflect.sample

import io.github.chronotask.ChronoTask
import io.github.chronotask.annotation.AnnotationConcurrency
import io.github.chronotask.annotation.AnnotationMisfire
import io.github.chronotask.annotation.Cron
import io.github.chronotask.annotation.Once
import io.github.chronotask.annotation.ScheduleMode
import io.github.chronotask.annotation.Scheduled
import io.github.chronotask.annotation.TickTask
import io.github.chronotask.engine.ChronoTaskEngine
import io.github.chronotask.job.TaskExecutionContext
import io.github.chronotask.reflect.registerAnnotated
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class SampleKotlinReflection {

    private lateinit var engine: ChronoTaskEngine

    @BeforeEach
    fun setUp() {
        engine = ChronoTask.create()
        engine.start()
    }

    @AfterEach
    fun tearDown() {
        engine.stop()
    }

    class SampleServices {
        val cleanupCount = AtomicInteger(0)
        val reportCount = AtomicInteger(0)
        val initCount = AtomicInteger(0)
        val tickCount = AtomicInteger(0)

        @Scheduled(
            id = "cleanup-service",
            mode = ScheduleMode.FIXED_DELAY,
            intervalMs = 50,
            initialDelayMs = 10,
            concurrency = AnnotationConcurrency.SKIP_IF_RUNNING,
            misfire = AnnotationMisfire.SKIP
        )
        suspend fun runCleanup(context: TaskExecutionContext) {
            delay(10)
            cleanupCount.incrementAndGet()
        }

        @Cron("*/1 * * * * ?", id = "cron-report")
        fun generateReport() {
            reportCount.incrementAndGet()
        }

        @Once(delayMs = 10, id = "init-task")
        suspend fun initDatabase() {
            initCount.incrementAndGet()
        }

        @TickTask(intervalMs = 50, id = "game-tick")
        fun onTick() {
            tickCount.incrementAndGet()
        }
    }

    @Test
    fun `sample demonstrates registering and running annotated kotlin methods`() = runBlocking {
        val services = SampleServices()
        val handles = engine.registerAnnotated(services)

        assertEquals(4, handles.size)
        assertNotNull(engine.getHandle("cleanup-service"))
        assertNotNull(engine.getHandle("cron-report"))
        assertNotNull(engine.getHandle("init-task"))
        assertNotNull(engine.getHandle("game-tick"))

        // Trigger tasks manually to verify execution
        engine.getHandle("cleanup-service")?.triggerNow()
        engine.getHandle("init-task")?.triggerNow()
        engine.getHandle("game-tick")?.triggerNow()

        delay(100)

        assertTrue(services.cleanupCount.get() >= 1)
        assertTrue(services.initCount.get() >= 1)
        assertTrue(services.tickCount.get() >= 1)
    }
}

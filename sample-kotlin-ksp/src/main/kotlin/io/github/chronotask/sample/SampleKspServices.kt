package io.github.chronotask.sample

import io.github.chronotask.annotation.AnnotationConcurrency
import io.github.chronotask.annotation.AnnotationMisfire
import io.github.chronotask.annotation.Cron
import io.github.chronotask.annotation.Once
import io.github.chronotask.annotation.ScheduleMode
import io.github.chronotask.annotation.Scheduled
import io.github.chronotask.annotation.TickTask
import io.github.chronotask.job.TaskExecutionContext
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger

class SampleKspServices {

    val cleanupCount = AtomicInteger(0)
    val reportCount = AtomicInteger(0)
    val initCount = AtomicInteger(0)
    val tickCount = AtomicInteger(0)

    @Scheduled(
        id = "sample-cleanup",
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

    @Cron(expression = "*/1 * * * * ?", id = "sample-cron-report")
    fun generateReport() {
        reportCount.incrementAndGet()
    }

    @Once(delayMs = 10, id = "sample-init-task")
    suspend fun initDatabase() {
        initCount.incrementAndGet()
    }

    @TickTask(intervalMs = 50, id = "sample-game-tick")
    fun onTick() {
        tickCount.incrementAndGet()
    }
}

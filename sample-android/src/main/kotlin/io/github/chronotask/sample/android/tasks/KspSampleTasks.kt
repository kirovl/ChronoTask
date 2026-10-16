package io.github.chronotask.sample.android.tasks

import io.github.chronotask.annotation.Cron
import io.github.chronotask.annotation.Once
import io.github.chronotask.annotation.Scheduled
import io.github.chronotask.annotation.TickTask
import io.github.chronotask.cron.CronDialect
import io.github.chronotask.job.TaskExecutionContext
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger

class KspSampleTasks(
    private val onTaskRun: (taskId: String, count: Int, message: String) -> Unit
) {
    private val analyticsCount = AtomicInteger(0)
    private val cronCount = AtomicInteger(0)
    private val onceCount = AtomicInteger(0)
    private val beaconCount = AtomicInteger(0)
    private val suspendCount = AtomicInteger(0)

    @Scheduled(id = "ksp-analytics-sync", intervalMs = 3000L, initialDelayMs = 1000L)
    suspend fun syncAnalytics(context: TaskExecutionContext) {
        val count = analyticsCount.incrementAndGet()
        delay(50L) // Suspend execution demonstration
        onTaskRun("ksp-analytics-sync", count, "Synced analytics events (batch #$count, src=${context.trigger.source})")
    }

    @Cron(id = "ksp-cron-cleanup", expression = "*/10 * * * * ?", dialect = CronDialect.QUARTZ)
    fun cronCleanup() {
        val count = cronCount.incrementAndGet()
        onTaskRun("ksp-cron-cleanup", count, "Cron cleanup executed (#$count)")
    }

    @Once(id = "ksp-onboarding-tip", delayMs = 1000L)
    fun showOnboardingTip() {
        val count = onceCount.incrementAndGet()
        onTaskRun("ksp-onboarding-tip", count, "One-shot onboarding initialized")
    }

    @TickTask(id = "ksp-location-beacon", intervalMs = 2000L, initialDelayMs = 2000L)
    fun beaconPulse() {
        val count = beaconCount.incrementAndGet()
        onTaskRun("ksp-location-beacon", count, "Beacon heartbeat pulse (#$count)")
    }

    @Scheduled(id = "ksp-suspend-checkpoint", intervalMs = 6000L, initialDelayMs = 3000L)
    suspend fun suspendCheckpoint(context: TaskExecutionContext) {
        val count = suspendCount.incrementAndGet()
        delay(100L)
        onTaskRun("ksp-suspend-checkpoint", count, "Database checkpoint completed asynchronously")
    }
}

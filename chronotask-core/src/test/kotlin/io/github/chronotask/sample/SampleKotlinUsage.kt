package io.github.chronotask.sample

import io.github.chronotask.ChronoTask
import io.github.chronotask.cron.CronDialect
import io.github.chronotask.dsl.schedule
import io.github.chronotask.job.ChronoJob
import io.github.chronotask.job.ManagedCronJob
import io.github.chronotask.job.TaskExecutionContext
import io.github.chronotask.job.register
import io.github.chronotask.model.TaskSchedule
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.ZoneId
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class SampleKotlinUsage {

    class DatabaseSyncJob : ChronoJob() {
        override val id = "db-sync"
        override val schedule = TaskSchedule.FixedRate(60_000L)

        override suspend fun execute(context: TaskExecutionContext) {
            println("Syncing database at ${context.trigger.scheduledAt}")
        }
    }

    class DailyReportJob : ManagedCronJob(
        id = "daily-report",
        cron = "0 0 9 * * ?",
        zoneId = ZoneId.of("Asia/Shanghai")
    ) {
        override suspend fun execute(context: TaskExecutionContext) {
            println("Generating daily report at ${context.trigger.scheduledAt}")
        }
    }

    @Test
    fun `demonstrate kotlin usage patterns`() = runBlocking {
        val engine = ChronoTask.create()

        // 1. Kotlin DSL
        val handle1 = engine.schedule("health-check") {
            fixedRate(interval = 10.seconds, initialDelay = 2.seconds)
            skipIfRunning()
            onMisfireSkip()
            execute { ctx ->
                println("Health check fired at ${ctx.trigger.scheduledAt}")
            }
        }

        // 2. Kotlin DSL Cron
        val handle2 = engine.schedule("hourly-cleanup") {
            cron("0 0 * * * ?", ZoneId.of("UTC"), CronDialect.QUARTZ)
            queueOne()
            onMisfireFireOnce()
            execute { ctx ->
                println("Cleaning up logs...")
            }
        }

        // 3. ChronoJob subclass
        val handle3 = engine.register(DatabaseSyncJob())

        // 4. ManagedCronJob subclass
        val handle4 = engine.register(DailyReportJob())

        // Lifecycle controls
        engine.start()
        handle1.pause()
        handle1.resume()
        handle2.triggerNow()
        engine.stop()
    }
}

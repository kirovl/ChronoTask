package io.github.chronotask.compat

import io.github.chronotask.ChronoTask
import io.github.chronotask.cron.CronDialect
import io.github.chronotask.dsl.TaskDsl
import io.github.chronotask.job.ChronoJob
import io.github.chronotask.job.ManagedCronJob
import io.github.chronotask.job.TaskExecutionContext
import io.github.chronotask.model.ConcurrencyPolicy
import io.github.chronotask.model.MisfirePolicy
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.ZoneId
import kotlin.time.Duration.Companion.seconds

class CrossApiEquivalenceTest {

    @Test
    fun `FixedRate task definition equivalence across DSL, Builder and ChronoJob`() {
        // Direct
        val direct = TaskDefinition(
            id = "equiv-fixed-rate",
            name = "Equivalence Rate",
            schedule = TaskSchedule.FixedRate(intervalMs = 5000L, initialDelayMs = 1000L),
            concurrencyPolicy = ConcurrencyPolicy.QueueOne,
            misfirePolicy = MisfirePolicy.FIRE_ONCE,
            dispatcherKey = "custom-pool",
            isEnabled = true,
            executable = { }
        )

        // DSL
        val dsl = TaskDsl("equiv-fixed-rate").apply {
            name = "Equivalence Rate"
            fixedRate(interval = 5.seconds, initialDelay = 1.seconds)
            queueOne()
            onMisfireFireOnce()
            dispatcher = "custom-pool"
            enabled = true
            execute { }
        }.build()

        // Builder
        val builder = ChronoTask.newTask("equiv-fixed-rate")
            .name("Equivalence Rate")
            .fixedRate(Duration.ofSeconds(5), Duration.ofSeconds(1))
            .queueOne()
            .onMisfireFireOnce()
            .dispatcher("custom-pool")
            .enabled(true)
            .executeRunnable { }
            .build()

        // ChronoJob
        val job = object : ChronoJob() {
            override val id: String = "equiv-fixed-rate"
            override val name: String = "Equivalence Rate"
            override val schedule: TaskSchedule = TaskSchedule.FixedRate(5000L, 1000L)
            override val concurrencyPolicy: ConcurrencyPolicy = ConcurrencyPolicy.QueueOne
            override val misfirePolicy: MisfirePolicy = MisfirePolicy.FIRE_ONCE
            override val dispatcherKey: String = "custom-pool"
            override val isEnabled: Boolean = true
            override suspend fun execute(context: TaskExecutionContext) {}
        }.toTaskDefinition()

        val list = listOf(direct, dsl, builder, job)
        for (item in list) {
            assertEquals("equiv-fixed-rate", item.id)
            assertEquals("Equivalence Rate", item.name)
            assertEquals(TaskSchedule.FixedRate(5000L, 1000L), item.schedule)
            assertEquals(ConcurrencyPolicy.QueueOne, item.concurrencyPolicy)
            assertEquals(MisfirePolicy.FIRE_ONCE, item.misfirePolicy)
            assertEquals("custom-pool", item.dispatcherKey)
            assertEquals(true, item.isEnabled)
        }
    }

    @Test
    fun `Cron task definition equivalence across DSL, Builder and ManagedCronJob`() {
        val zone = ZoneId.of("Asia/Tokyo")

        // DSL
        val dsl = TaskDsl("equiv-cron").apply {
            cron("0 0 * * * ?", zone, CronDialect.QUARTZ)
            execute { }
        }.build()

        // Builder
        val builder = ChronoTask.newTask("equiv-cron")
            .cron("0 0 * * * ?", zone, CronDialect.QUARTZ)
            .executeRunnable { }
            .build()

        // ManagedCronJob
        val managedCron = object : ManagedCronJob(
            id = "equiv-cron",
            cron = "0 0 * * * ?",
            zoneId = zone,
            dialect = CronDialect.QUARTZ
        ) {
            override suspend fun execute(context: TaskExecutionContext) {}
        }.toTaskDefinition()

        val list = listOf(dsl, builder, managedCron)
        for (item in list) {
            assertEquals("equiv-cron", item.id)
            assertEquals("equiv-cron", item.name)
            assertEquals(TaskSchedule.Cron("0 0 * * * ?", zone, CronDialect.QUARTZ), item.schedule)
            assertEquals(ConcurrencyPolicy.SkipIfRunning, item.concurrencyPolicy)
            assertEquals(MisfirePolicy.Skip, item.misfirePolicy)
            assertEquals(null, item.dispatcherKey)
            assertEquals(true, item.isEnabled)
        }
    }
}

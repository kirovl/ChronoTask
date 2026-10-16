package io.github.chronotask.reflect.mapping

import io.github.chronotask.ChronoTask
import io.github.chronotask.annotation.AnnotationConcurrency
import io.github.chronotask.annotation.AnnotationMisfire
import io.github.chronotask.annotation.Cron
import io.github.chronotask.annotation.ScheduleMode
import io.github.chronotask.annotation.Scheduled
import io.github.chronotask.cron.CronDialect
import io.github.chronotask.dsl.TaskDsl
import io.github.chronotask.job.ChronoJob
import io.github.chronotask.job.ManagedCronJob
import io.github.chronotask.job.TaskExecutionContext
import io.github.chronotask.model.ConcurrencyPolicy
import io.github.chronotask.model.MisfirePolicy
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.reflect.ReflectionTaskScanner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.ZoneId
import kotlin.time.Duration.Companion.seconds

class CrossApiEquivalenceTest {

    class AnnotatedReflectService {
        @Scheduled(
            intervalMs = 5000,
            initialDelayMs = 1000,
            mode = ScheduleMode.FIXED_RATE,
            id = "equiv-task",
            name = "Equivalence Task",
            concurrency = AnnotationConcurrency.QUEUE_ONE,
            misfire = AnnotationMisfire.FIRE_ONCE,
            dispatcher = "custom-pool",
            enabled = true
        )
        fun scheduledTask() {}

        @Cron(
            expression = "0 0 12 * * ?",
            zone = "UTC",
            dialect = CronDialect.QUARTZ,
            id = "equiv-cron",
            name = "Equivalence Cron"
        )
        fun cronTask() {}
    }

    @Test
    fun `five task definition pathways produce 100 percent equivalent attributes`() {
        // 1. Direct
        val direct = TaskDefinition(
            id = "equiv-task",
            name = "Equivalence Task",
            schedule = TaskSchedule.FixedRate(5000L, 1000L),
            concurrencyPolicy = ConcurrencyPolicy.QueueOne,
            misfirePolicy = MisfirePolicy.FIRE_ONCE,
            dispatcherKey = "custom-pool",
            isEnabled = true,
            executable = { }
        )

        // 2. DSL
        val dsl = TaskDsl("equiv-task").apply {
            name = "Equivalence Task"
            fixedRate(interval = 5.seconds, initialDelay = 1.seconds)
            queueOne()
            onMisfireFireOnce()
            dispatcher = "custom-pool"
            enabled = true
            execute { }
        }.build()

        // 3. Builder
        val builder = ChronoTask.newTask("equiv-task")
            .name("Equivalence Task")
            .fixedRate(Duration.ofSeconds(5), Duration.ofSeconds(1))
            .queueOne()
            .onMisfireFireOnce()
            .dispatcher("custom-pool")
            .enabled(true)
            .executeRunnable { }
            .build()

        // 4. ChronoJob
        val job = object : ChronoJob() {
            override val id: String = "equiv-task"
            override val name: String = "Equivalence Task"
            override val schedule: TaskSchedule = TaskSchedule.FixedRate(5000L, 1000L)
            override val concurrencyPolicy: ConcurrencyPolicy = ConcurrencyPolicy.QueueOne
            override val misfirePolicy: MisfirePolicy = MisfirePolicy.FIRE_ONCE
            override val dispatcherKey: String = "custom-pool"
            override val isEnabled: Boolean = true
            override suspend fun execute(context: TaskExecutionContext) {}
        }.toTaskDefinition()

        // 5. Reflection Annotation
        val reflect = ReflectionTaskScanner.scan(AnnotatedReflectService()).first { it.id == "equiv-task" }

        val all = listOf(direct, dsl, builder, job, reflect)
        for (item in all) {
            assertEquals("equiv-task", item.id)
            assertEquals("Equivalence Task", item.name)
            assertEquals(TaskSchedule.FixedRate(5000L, 1000L), item.schedule)
            assertEquals(ConcurrencyPolicy.QueueOne, item.concurrencyPolicy)
            assertEquals(MisfirePolicy.FIRE_ONCE, item.misfirePolicy)
            assertEquals("custom-pool", item.dispatcherKey)
            assertEquals(true, item.isEnabled)
        }
    }
}

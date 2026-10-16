package io.github.chronotask.ksp.equivalence

import io.github.chronotask.ChronoTask
import io.github.chronotask.annotation.AnnotationConcurrency
import io.github.chronotask.annotation.AnnotationMisfire
import io.github.chronotask.annotation.Cron
import io.github.chronotask.annotation.Once
import io.github.chronotask.annotation.ScheduleMode
import io.github.chronotask.annotation.Scheduled
import io.github.chronotask.annotation.TickTask
import io.github.chronotask.job.TaskExecutionContext
import io.github.chronotask.reflect.registerAnnotated
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class EquivalenceTarget {

    @Scheduled(
        intervalMs = 10000,
        initialDelayMs = 2000,
        mode = ScheduleMode.FIXED_RATE,
        id = "rate-task",
        name = "Rate Task Name",
        concurrency = AnnotationConcurrency.SKIP_IF_RUNNING,
        misfire = AnnotationMisfire.SKIP
    )
    fun rateTask(ctx: TaskExecutionContext) {}

    @Scheduled(
        intervalMs = 15000,
        initialDelayMs = 3000,
        mode = ScheduleMode.FIXED_DELAY,
        id = "delay-task",
        concurrency = AnnotationConcurrency.ALLOW,
        misfire = AnnotationMisfire.FIRE_ONCE
    )
    suspend fun delayTask() {}

    @Cron(
        expression = "0 0 12 * * ?",
        zone = "UTC",
        id = "cron-task",
        concurrency = AnnotationConcurrency.QUEUE_ONE,
        misfire = AnnotationMisfire.CATCH_UP,
        catchUpLimit = 25
    )
    fun cronTask() {}

    @Once(
        delayMs = 5000,
        id = "once-task",
        concurrency = AnnotationConcurrency.SERIAL,
        serialQueueSize = 512
    )
    suspend fun onceTask(ctx: TaskExecutionContext) {}

    @TickTask(
        intervalMs = 250,
        initialDelayMs = 50,
        id = "tick-task"
    )
    fun tickTask() {}
}

class ReflectionKspEquivalenceTest {

    @Test
    fun `Reflection and KSP generate 100 percent identical TaskDefinition semantics`() {
        val target = EquivalenceTarget()

        val engineRef = ChronoTask.create()
        val refHandles = engineRef.registerAnnotated(target)

        val kspDefs = EquivalenceTarget_ChronoTaskBinding.definitions(target)

        assertEquals(refHandles.size, kspDefs.size, "Task definition count must match")

        val refTaskIds = refHandles.map { it.taskId }.toSet()
        val kspTaskIds = kspDefs.map { it.id }.toSet()
        assertEquals(refTaskIds, kspTaskIds)

        for (kspDef in kspDefs) {
            val refHandle = engineRef.getHandle(kspDef.id)
            assertNotNull(refHandle, "Missing task in Reflection: ${kspDef.id}")
            val snapshot = refHandle!!.snapshot()
            assertEquals(kspDef.id, snapshot.taskId)
        }

        engineRef.stop()
    }
}

package io.github.chronotask.job

import io.github.chronotask.model.ConcurrencyPolicy
import io.github.chronotask.model.MisfirePolicy
import io.github.chronotask.model.TaskSchedule
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean

class ChronoJobTest {

    private class DefaultJob : ChronoJob() {
        override val id: String = "default-job"
        override val schedule: TaskSchedule = TaskSchedule.FixedRate(1000L)
        override suspend fun execute(context: TaskExecutionContext) {}
    }

    private class CustomJob(private val flag: AtomicBoolean) : ChronoJob() {
        override val id: String = "custom-job"
        override val name: String = "Custom Job Name"
        override val schedule: TaskSchedule = TaskSchedule.FixedDelay(5000L, 1000L)
        override val concurrencyPolicy: ConcurrencyPolicy = ConcurrencyPolicy.Allow
        override val misfirePolicy: MisfirePolicy = MisfirePolicy.FIRE_ONCE
        override val dispatcherKey: String = "custom-dispatcher"
        override val isEnabled: Boolean = false

        override suspend fun execute(context: TaskExecutionContext) {
            flag.set(true)
        }
    }

    @Test
    fun `default job values map correctly to TaskDefinition`() {
        val job = DefaultJob()
        val def = job.toTaskDefinition()

        assertEquals("default-job", def.id)
        assertEquals("default-job", def.name)
        assertEquals(TaskSchedule.FixedRate(1000L), def.schedule)
        assertEquals(ConcurrencyPolicy.SkipIfRunning, def.concurrencyPolicy)
        assertEquals(MisfirePolicy.Skip, def.misfirePolicy)
        assertNull(def.dispatcherKey)
        assertTrue(def.isEnabled)
    }

    @Test
    fun `custom job values map correctly and execute logic is preserved`() = runBlocking {
        val flag = AtomicBoolean(false)
        val job = CustomJob(flag)
        val def = job.toTaskDefinition()

        assertEquals("custom-job", def.id)
        assertEquals("Custom Job Name", def.name)
        assertEquals(TaskSchedule.FixedDelay(5000L, 1000L), def.schedule)
        assertEquals(ConcurrencyPolicy.Allow, def.concurrencyPolicy)
        assertEquals(MisfirePolicy.FIRE_ONCE, def.misfirePolicy)
        assertEquals("custom-dispatcher", def.dispatcherKey)
        assertEquals(false, def.isEnabled)

        // Test executable invocation
        val dummyContext = TaskExecutionContext(
            taskId = "custom-job",
            trigger = io.github.chronotask.trigger.ScheduledTrigger(
                scheduledAt = java.time.Instant.EPOCH,
                discoveredAt = java.time.Instant.EPOCH
            )
        )
        def.executable.execute(dummyContext)
        assertTrue(flag.get())
    }
}

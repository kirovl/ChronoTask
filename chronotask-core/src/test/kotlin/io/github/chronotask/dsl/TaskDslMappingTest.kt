package io.github.chronotask.dsl

import io.github.chronotask.model.ConcurrencyPolicy
import io.github.chronotask.model.MisfirePolicy
import io.github.chronotask.model.QueueOverflowPolicy
import io.github.chronotask.model.TaskSchedule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class TaskDslMappingTest {

    @Test
    fun `maps all properties accurately to TaskDefinition`() {
        val dsl = TaskDsl("full-task").apply {
            name = "Full Mapped Task"
            fixedRate(interval = 10.seconds, initialDelay = 2.seconds)
            concurrency = ConcurrencyPolicy.Serial(maxQueueSize = 50, overflowPolicy = QueueOverflowPolicy.REJECT)
            misfire = MisfirePolicy.CatchUp(maxExecutions = 10)
            dispatcherKey = "db-pool"
            enabled = true
            execute { _ -> /* no-op */ }
        }

        val def = dsl.build()
        assertEquals("full-task", def.id)
        assertEquals("Full Mapped Task", def.name)
        val schedule = assertInstanceOf(TaskSchedule.FixedRate::class.java, def.schedule)
        assertEquals(10000L, schedule.intervalMs)
        assertEquals(2000L, schedule.initialDelayMs)

        val concurrency = assertInstanceOf(ConcurrencyPolicy.Serial::class.java, def.concurrencyPolicy)
        assertEquals(50, concurrency.maxQueueSize)
        assertEquals(QueueOverflowPolicy.REJECT, concurrency.overflowPolicy)

        val misfire = assertInstanceOf(MisfirePolicy.CatchUp::class.java, def.misfirePolicy)
        assertEquals(10, misfire.maxExecutions)

        assertEquals("db-pool", def.dispatcherKey)
        assertTrue(def.isEnabled)
    }
}

package io.github.chronotask.reflect.mapping

import io.github.chronotask.annotation.AnnotationConcurrency
import io.github.chronotask.annotation.AnnotationMisfire
import io.github.chronotask.annotation.ScheduleMode
import io.github.chronotask.annotation.Scheduled
import io.github.chronotask.annotation.TickTask
import io.github.chronotask.model.QueueOverflowPolicy
import io.github.chronotask.reflect.ReflectionTaskScanner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TickTaskEquivalenceTest {

    class TickService {
        @TickTask(
            intervalMs = 5000,
            initialDelayMs = 1000,
            id = "same-id",
            name = "Same Name",
            concurrency = AnnotationConcurrency.QUEUE_ONE,
            misfire = AnnotationMisfire.FIRE_ONCE,
            dispatcher = "test-dispatcher",
            enabled = true,
            serialQueueSize = 512,
            serialOverflow = QueueOverflowPolicy.DROP_OLDEST
        )
        fun tickMethod() {}
    }

    class ScheduledService {
        @Scheduled(
            intervalMs = 5000,
            initialDelayMs = 1000,
            mode = ScheduleMode.FIXED_RATE,
            id = "same-id",
            name = "Same Name",
            concurrency = AnnotationConcurrency.QUEUE_ONE,
            misfire = AnnotationMisfire.FIRE_ONCE,
            dispatcher = "test-dispatcher",
            enabled = true,
            serialQueueSize = 512,
            serialOverflow = QueueOverflowPolicy.DROP_OLDEST
        )
        fun scheduledMethod() {}
    }

    @Test
    fun `TickTask and Scheduled produce identical TaskDefinition attributes`() {
        val def1 = ReflectionTaskScanner.scan(TickService()).single()
        val def2 = ReflectionTaskScanner.scan(ScheduledService()).single()

        assertEquals(def1.id, def2.id)
        assertEquals(def1.name, def2.name)
        assertEquals(def1.schedule, def2.schedule)
        assertEquals(def1.concurrencyPolicy, def2.concurrencyPolicy)
        assertEquals(def1.misfirePolicy, def2.misfirePolicy)
        assertEquals(def1.dispatcherKey, def2.dispatcherKey)
        assertEquals(def1.isEnabled, def2.isEnabled)
    }
}

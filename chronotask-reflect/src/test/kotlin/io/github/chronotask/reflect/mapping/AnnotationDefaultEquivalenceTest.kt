package io.github.chronotask.reflect.mapping

import io.github.chronotask.annotation.Cron
import io.github.chronotask.annotation.Once
import io.github.chronotask.annotation.Scheduled
import io.github.chronotask.annotation.TickTask
import io.github.chronotask.model.ConcurrencyPolicy
import io.github.chronotask.model.MisfirePolicy
import io.github.chronotask.reflect.ReflectionTaskScanner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnnotationDefaultEquivalenceTest {

    class DefaultService {
        @Scheduled(intervalMs = 1000)
        fun scheduled() {}

        @Cron(expression = "0 0 * * * ?")
        fun cron() {}

        @Once
        fun once() {}

        @TickTask(intervalMs = 1000)
        fun tick() {}
    }

    @Test
    fun `all annotation types produce consistent default policies aligning with M1-M7`() {
        val defs = ReflectionTaskScanner.scan(DefaultService())
        assertEquals(4, defs.size)

        for (def in defs) {
            assertEquals(ConcurrencyPolicy.SkipIfRunning, def.concurrencyPolicy, "Default concurrency must be SkipIfRunning for ${def.id}")
            assertEquals(MisfirePolicy.Skip, def.misfirePolicy, "Default misfire must be Skip for ${def.id}")
            assertNull(def.dispatcherKey, "Default dispatcher must be null for ${def.id}")
            assertTrue(def.isEnabled, "Default enabled must be true for ${def.id}")
            assertEquals(def.id, def.name, "Default name must equal id for ${def.id}")
        }
    }
}

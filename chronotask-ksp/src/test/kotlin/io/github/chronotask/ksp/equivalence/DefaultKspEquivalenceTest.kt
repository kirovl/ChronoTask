package io.github.chronotask.ksp.equivalence

import io.github.chronotask.annotation.Scheduled
import io.github.chronotask.model.ConcurrencyPolicy
import io.github.chronotask.model.MisfirePolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultTarget {
    @Scheduled(intervalMs = 5000)
    fun defaultMethod() {}
}

class DefaultKspEquivalenceTest {

    @Test
    fun `default values match M1 and M8 contract`() {
        val target = DefaultTarget()
        val defs = DefaultTarget_ChronoTaskBinding.definitions(target)

        assertEquals(1, defs.size)
        val def = defs.single()

        val expectedId = "io.github.chronotask.ksp.equivalence.DefaultTarget#defaultMethod"
        assertEquals(expectedId, def.id)
        assertEquals(expectedId, def.name)
        assertEquals(ConcurrencyPolicy.SkipIfRunning, def.concurrencyPolicy)
        assertEquals(MisfirePolicy.SKIP, def.misfirePolicy)
        assertNull(def.dispatcherKey)
        assertTrue(def.isEnabled)
    }
}

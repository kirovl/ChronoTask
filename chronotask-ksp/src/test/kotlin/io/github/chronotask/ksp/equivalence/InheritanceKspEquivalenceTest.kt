package io.github.chronotask.ksp.equivalence

import io.github.chronotask.ChronoTask
import io.github.chronotask.annotation.Scheduled
import io.github.chronotask.reflect.registerAnnotated
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

open class EquivalenceBase {
    @Scheduled(intervalMs = 1000, id = "eq-base-task")
    open fun baseTask() {}

    @Scheduled(intervalMs = 2000, id = "eq-to-suppress")
    open fun toSuppress() {}

    @Scheduled(intervalMs = 3000, id = "eq-to-override")
    open fun toOverride() {}
}

class EquivalenceChild : EquivalenceBase() {
    override fun toSuppress() {}

    @Scheduled(intervalMs = 4000, id = "eq-overridden-task")
    override fun toOverride() {}

    @Scheduled(intervalMs = 5000, id = "eq-child-task")
    fun childTask() {}
}

class InheritanceKspEquivalenceTest {

    @Test
    fun `Reflection and KSP generate identical effective task list across inheritance`() {
        val child = EquivalenceChild()

        val engine = ChronoTask.create()
        val refHandles = engine.registerAnnotated(child)
        val kspDefs = EquivalenceChild_ChronoTaskBinding.definitions(child)

        assertEquals(refHandles.size, kspDefs.size)
        val refIds = refHandles.map { it.taskId }.toSet()
        val kspIds = kspDefs.map { it.id }.toSet()

        assertEquals(refIds, kspIds)
        engine.stop()
    }
}

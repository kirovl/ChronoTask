package io.github.chronotask.ksp.generation

import io.github.chronotask.annotation.Once
import io.github.chronotask.annotation.Scheduled
import io.github.chronotask.annotation.TickTask
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DeterministicTarget {
    @Scheduled(intervalMs = 3000, id = "c-task")
    fun c() {}

    @TickTask(intervalMs = 1000, id = "a-task")
    fun a() {}

    @Once(delayMs = 2000, id = "b-task")
    fun b() {}
}

class DeterministicGenerationTest {

    @Test
    fun `task definitions are deterministically ordered by method name and id`() {
        val target = DeterministicTarget()
        val defs1 = DeterministicTarget_ChronoTaskBinding.definitions(target)
        val defs2 = DeterministicTarget_ChronoTaskBinding.definitions(target)

        assertEquals(3, defs1.size)
        assertEquals(defs1.map { it.id }, defs2.map { it.id })

        val ids = defs1.map { it.id }
        assertEquals(listOf("a-task", "b-task", "c-task"), ids)
    }
}

package io.github.chronotask.ksp.generation

import io.github.chronotask.annotation.Once
import io.github.chronotask.engine.DefaultChronoTaskEngine
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.reflect.AnnotatedTaskRegistrar
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DefaultIdContainer {
    class NestedJob {
        @Once(enabled = false) fun run() {}
    }
}

internal class InternalDefaultIdJob {
    @Once(enabled = false) internal fun run() {}
    @Once(enabled = false) internal suspend fun suspendRun() {}
}

class RenamedDefaultIdJob {
    @JvmName("jvmRun")
    @Once(enabled = false)
    fun sourceRun() {}
}

class DefaultIdParityTest {
    private fun assertParity(target: Any, definitions: List<TaskDefinition>, expected: Set<String>) {
        DefaultChronoTaskEngine().use { engine ->
            val reflected = AnnotatedTaskRegistrar.register(engine, target).map { it.taskId }.toSet()
            assertEquals(expected, definitions.map { it.id }.toSet())
            assertEquals(expected, reflected)
            assertEquals(expected, definitions.map { it.name }.toSet())
        }
    }

    @Test fun `nested class default IDs match source qualified names`() {
        val target = DefaultIdContainer.NestedJob()
        assertParity(target, DefaultIdContainer_NestedJob_ChronoTaskBinding.definitions(target),
            setOf("io.github.chronotask.ksp.generation.DefaultIdContainer.NestedJob#run"))
    }

    @Test fun `internal sync and suspend default IDs omit JVM module mangling`() {
        val target = InternalDefaultIdJob()
        assertParity(target, InternalDefaultIdJob_ChronoTaskBinding.definitions(target),
            setOf("io.github.chronotask.ksp.generation.InternalDefaultIdJob#run",
                "io.github.chronotask.ksp.generation.InternalDefaultIdJob#suspendRun"))
    }

    @Test fun `JvmName methods use Kotlin source names for default IDs`() {
        val target = RenamedDefaultIdJob()
        assertParity(target, RenamedDefaultIdJob_ChronoTaskBinding.definitions(target),
            setOf("io.github.chronotask.ksp.generation.RenamedDefaultIdJob#sourceRun"))
    }
}

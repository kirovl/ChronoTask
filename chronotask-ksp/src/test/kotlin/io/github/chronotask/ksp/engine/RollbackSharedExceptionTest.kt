package io.github.chronotask.ksp.engine

import io.github.chronotask.annotation.Once
import io.github.chronotask.engine.ChronoTaskEngine
import io.github.chronotask.engine.DefaultChronoTaskEngine
import io.github.chronotask.engine.TaskHandle
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.reflect.AnnotatedTaskRegistrar
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class SharedExceptionJobs {
    @Once(id = "shared-a") fun a() {}
    @Once(id = "shared-b") fun b() {}
    @Once(id = "shared-c") fun c() {}
    @Once(id = "shared-d") fun d() {}
}

class RollbackSharedExceptionTest {
    enum class Binding { KSP, REFLECTION }

    private fun verify(binding: Binding, sameAsRegistration: Boolean) {
        val failure = IllegalStateException("registration failure")
        val rollbackFailure = if (sameAsRegistration) failure else IllegalStateException("shared rollback failure")
        val rolledBack = mutableListOf<String>()
        DefaultChronoTaskEngine().use { delegate ->
            val engine = object : ChronoTaskEngine by delegate {
                override fun register(definition: TaskDefinition): TaskHandle {
                    if (definition.id == "shared-d") throw failure
                    val handle = delegate.register(definition)
                    return object : TaskHandle by handle {
                        override fun unregister(): Boolean {
                            rolledBack += taskId
                            handle.unregister()
                            throw rollbackFailure
                        }
                    }
                }
            }
            val target = SharedExceptionJobs()
            val thrown = assertThrows(IllegalStateException::class.java) {
                when (binding) {
                    Binding.KSP -> SharedExceptionJobs_ChronoTaskBinding.register(engine, target)
                    Binding.REFLECTION -> AnnotatedTaskRegistrar.register(engine, target)
                }
            }
            assertSame(failure, thrown)
            assertEquals(listOf("shared-c", "shared-b", "shared-a"), rolledBack)
            for (id in rolledBack) assertNull(delegate.getHandle(id))
            if (sameAsRegistration) assertTrue(thrown.suppressed.isEmpty())
            else assertSame(rollbackFailure, thrown.suppressed.first())
        }
    }

    @ParameterizedTest
    @EnumSource(Binding::class)
    fun `rollback throwing original exception does not interrupt cleanup`(binding: Binding) = verify(binding, true)

    @ParameterizedTest
    @EnumSource(Binding::class)
    fun `reused rollback exception does not interrupt cleanup`(binding: Binding) = verify(binding, false)
}

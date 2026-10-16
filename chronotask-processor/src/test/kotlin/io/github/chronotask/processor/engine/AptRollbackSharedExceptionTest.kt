package io.github.chronotask.processor.engine

import io.github.chronotask.engine.ChronoTaskEngine
import io.github.chronotask.engine.DefaultChronoTaskEngine
import io.github.chronotask.engine.TaskHandle
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.processor.TestCompiler
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.lang.reflect.InvocationTargetException

class AptRollbackSharedExceptionTest {
    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `reused rollback exceptions preserve original error and cleanup`(sameAsRegistration: Boolean) {
        TestCompiler.compileAndLoad("com.example.SharedExceptionJobs" to """
            package com.example;
            import io.github.chronotask.annotation.Once;
            public class SharedExceptionJobs {
                @Once(id = "shared-a") public void a() {}
                @Once(id = "shared-b") public void b() {}
                @Once(id = "shared-c") public void c() {}
                @Once(id = "shared-d") public void d() {}
            }
        """.trimIndent()).use { env ->
            val failure = IllegalStateException("registration failure")
            val rollbackFailure = if (sameAsRegistration) failure else IllegalStateException("rollback failure")
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
                val target = env.loadClass("com.example.SharedExceptionJobs").getDeclaredConstructor().newInstance()
                val thrown = assertThrows(InvocationTargetException::class.java) {
                    env.register("com.example.SharedExceptionJobs_ChronoTaskBinding", engine, target)
                }.targetException
                assertSame(failure, thrown)
                assertEquals(listOf("shared-c", "shared-b", "shared-a"), rolledBack)
                for (id in rolledBack) assertNull(delegate.getHandle(id))
                if (sameAsRegistration) assertTrue(thrown.suppressed.isEmpty())
                else assertSame(rollbackFailure, thrown.suppressed.first())
            }
        }
    }
}

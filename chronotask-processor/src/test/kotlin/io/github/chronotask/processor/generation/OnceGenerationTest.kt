package io.github.chronotask.processor.generation

import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.processor.TestCompiler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OnceGenerationTest {

    @Test
    fun testOnceTaskGeneration() {
        TestCompiler.compileAndLoad(
            "com.example.OnceJob" to """
                package com.example;

                import io.github.chronotask.annotation.Once;

                public class OnceJob {
                    @Once(delayMs = 1500L)
                    public void executeOnce() {}
                }
            """.trimIndent()
        ).use { env ->
            val targetClass = env.loadClass("com.example.OnceJob")
            val targetInstance = targetClass.getDeclaredConstructor().newInstance()

            val defs = env.getDefinitions("com.example.OnceJob_ChronoTaskBinding", targetInstance)
            assertEquals(1, defs.size)

            val def = defs[0]
            assertEquals("com.example.OnceJob#executeOnce", def.id)
            assertEquals(TaskSchedule.Once(1500L), def.schedule)
        }
    }
}

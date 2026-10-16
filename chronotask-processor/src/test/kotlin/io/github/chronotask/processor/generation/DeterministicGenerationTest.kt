package io.github.chronotask.processor.generation

import io.github.chronotask.processor.TestCompiler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DeterministicGenerationTest {

    @Test
    fun testTaskDefinitionsOrderIsDeterministic() {
        TestCompiler.compileAndLoad(
            "com.example.MultiTaskJob" to """
                package com.example;

                import io.github.chronotask.annotation.Scheduled;

                public class MultiTaskJob {
                    @Scheduled(id = "z-task", intervalMs = 1000L)
                    public void taskZ() {}

                    @Scheduled(id = "a-task", intervalMs = 1000L)
                    public void taskA() {}

                    @Scheduled(id = "m-task", intervalMs = 1000L)
                    public void taskM() {}
                }
            """.trimIndent()
        ).use { env ->
            val targetClass = env.loadClass("com.example.MultiTaskJob")
            val targetInstance = targetClass.getDeclaredConstructor().newInstance()

            val defs = env.getDefinitions("com.example.MultiTaskJob_ChronoTaskBinding", targetInstance)
            assertEquals(3, defs.size)

            // Should be sorted by methodName: taskA, taskM, taskZ
            assertEquals("a-task", defs[0].id)
            assertEquals("m-task", defs[1].id)
            assertEquals("z-task", defs[2].id)
        }
    }
}

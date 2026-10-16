package io.github.chronotask.processor.generation

import io.github.chronotask.processor.TestCompiler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NestedClassGenerationTest {

    @Test
    fun testStaticNestedClassBindingGeneration() {
        TestCompiler.compileAndLoad(
            "com.example.OuterService" to """
                package com.example;

                import io.github.chronotask.annotation.Scheduled;

                public class OuterService {
                    public static class InnerService {
                        @Scheduled(intervalMs = 1000L)
                        public void innerRun() {}
                    }
                }
            """.trimIndent()
        ).use { env ->
            val targetClass = env.loadClass("com.example.OuterService\$InnerService")
            val targetInstance = targetClass.getDeclaredConstructor().newInstance()

            val defs = env.getDefinitions("com.example.OuterService_InnerService_ChronoTaskBinding", targetInstance)
            assertEquals(1, defs.size)
            assertEquals("com.example.OuterService.InnerService#innerRun", defs[0].id)
        }
    }
}

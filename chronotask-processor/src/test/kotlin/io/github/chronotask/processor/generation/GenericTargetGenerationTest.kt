package io.github.chronotask.processor.generation

import io.github.chronotask.processor.TestCompiler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GenericTargetGenerationTest {

    @Test
    fun testGenericClassGeneration() {
        TestCompiler.compileAndLoad(
            "com.example.GenericService" to """
                package com.example;

                import io.github.chronotask.annotation.Scheduled;

                public class GenericService<T> {
                    @Scheduled(intervalMs = 1000L)
                    public void processItem() {}
                }
            """.trimIndent()
        ).use { env ->
            val targetClass = env.loadClass("com.example.GenericService")
            val targetInstance = targetClass.getDeclaredConstructor().newInstance()

            val defs = env.getDefinitions("com.example.GenericService_ChronoTaskBinding", targetInstance)
            assertEquals(1, defs.size)

            val source = env.getGeneratedSource("com.example.GenericService_ChronoTaskBinding")
            assertTrue(source.contains("public static <T> List<TaskDefinition> definitions(GenericService<T> target)"))
            assertTrue(source.contains("public static <T> List<TaskHandle> register(ChronoTaskEngine engine, GenericService<T> target)"))
        }
    }
}

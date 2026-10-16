package io.github.chronotask.processor.generation

import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.processor.TestCompiler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TickTaskGenerationTest {

    @Test
    fun testTickTaskGeneration() {
        TestCompiler.compileAndLoad(
            "com.example.TickJob" to """
                package com.example;

                import io.github.chronotask.annotation.TickTask;

                public class TickJob {
                    @TickTask(intervalMs = 100L)
                    public void onTick() {}
                }
            """.trimIndent()
        ).use { env ->
            val targetClass = env.loadClass("com.example.TickJob")
            val targetInstance = targetClass.getDeclaredConstructor().newInstance()

            val defs = env.getDefinitions("com.example.TickJob_ChronoTaskBinding", targetInstance)
            assertEquals(1, defs.size)

            val def = defs[0]
            assertEquals("com.example.TickJob#onTick", def.id)
            assertEquals(TaskSchedule.FixedRate(100L, 0L), def.schedule)
        }
    }
}

package io.github.chronotask.processor.inheritance

import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.processor.TestCompiler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AptAnnotatedOverrideTest {

    @Test
    fun testSubclassAnnotatedOverrideReplacesBaseTask() {
        TestCompiler.compileAndLoad(
            "com.example.BaseJob" to """
                package com.example;

                import io.github.chronotask.annotation.Scheduled;

                public class BaseJob {
                    @Scheduled(id = "base-task", intervalMs = 5000L)
                    public void work() {}
                }
            """.trimIndent(),
            "com.example.ChildJob" to """
                package com.example;

                import io.github.chronotask.annotation.Scheduled;

                public class ChildJob extends BaseJob {
                    @Override
                    @Scheduled(id = "child-task", intervalMs = 1000L)
                    public void work() {}
                }
            """.trimIndent()
        ).use { env ->
            val targetClass = env.loadClass("com.example.ChildJob")
            val targetInstance = targetClass.getDeclaredConstructor().newInstance()

            val defs = env.getDefinitions("com.example.ChildJob_ChronoTaskBinding", targetInstance)
            assertEquals(1, defs.size)

            val def = defs[0]
            assertEquals("child-task", def.id)
            assertEquals(TaskSchedule.FixedRate(1000L, 0L), def.schedule)
        }
    }
}

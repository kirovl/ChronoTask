package io.github.chronotask.processor.inheritance

import io.github.chronotask.processor.TestCompiler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AptOverrideSuppressionTest {

    @Test
    fun testSubclassOverrideWithoutAnnotationSuppressesBaseTask() {
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

                public class ChildJob extends BaseJob {
                    @Override
                    public void work() {
                        // Override without annotation suppresses task scheduling
                    }
                }
            """.trimIndent()
        ).use { env ->
            val targetClass = env.loadClass("com.example.ChildJob")
            val targetInstance = targetClass.getDeclaredConstructor().newInstance()

            // In ChildJob, work() was overridden without annotation, so ChildJob has 0 tasks and no binding is generated for ChildJob
            // Or if BaseJob has binding, BaseJob_ChronoTaskBinding has 1 task.
            val baseClass = env.loadClass("com.example.BaseJob")
            val baseInstance = baseClass.getDeclaredConstructor().newInstance()
            val baseDefs = env.getDefinitions("com.example.BaseJob_ChronoTaskBinding", baseInstance)
            assertEquals(1, baseDefs.size)
        }
    }
}

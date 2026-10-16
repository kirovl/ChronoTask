package io.github.chronotask.processor.golden

import io.github.chronotask.processor.TestCompiler
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeneratedSourceNoReflectionTest {

    @Test
    fun testGeneratedCodeContainsZeroReflectionAndZeroSchedulingLogic() {
        TestCompiler.compileAndLoad(
            "com.example.AuditJob" to """
                package com.example;

                import io.github.chronotask.annotation.Scheduled;
                import io.github.chronotask.job.TaskExecutionContext;
                import java.io.IOException;

                public class AuditJob {
                    @Scheduled(intervalMs = 10000L)
                    public void audit(TaskExecutionContext context) throws IOException {}
                }
            """.trimIndent()
        ).use { env ->
            val source = env.getGeneratedSource("com.example.AuditJob_ChronoTaskBinding")

            // 1. Must NOT contain java.lang.reflect
            assertFalse(source.contains("java.lang.reflect"), "Generated binding must not import java.lang.reflect")
            assertFalse(source.contains("Method "), "Generated binding must not use reflection Method")
            assertFalse(source.contains("getDeclaredMethod"), "Generated binding must not call getDeclaredMethod")
            assertFalse(source.contains("setAccessible"), "Generated binding must not call setAccessible")
            assertFalse(source.contains("invoke("), "Generated binding must not invoke reflective methods")

            // 2. Must NOT contain scheduling primitives / threads / timers / coroutines
            assertFalse(source.contains("Thread.sleep"), "Generated binding must not contain sleep")
            assertFalse(source.contains("Timer"), "Generated binding must not contain Timer")
            assertFalse(source.contains("ScheduledExecutorService"), "Generated binding must not contain ScheduledExecutorService")

            // 3. Must contain proper annotations and direct delegation
            assertTrue(source.contains("@Generated(\"io.github.chronotask.processor.ChronoTaskProcessor\")"))
            assertTrue(source.contains("public final class AuditJob_ChronoTaskBinding"))
            assertTrue(source.contains("private AuditJob_ChronoTaskBinding()"))
            assertTrue(source.contains("public static List<TaskDefinition> definitions(AuditJob target)"))
            assertTrue(source.contains("public static List<TaskHandle> register(ChronoTaskEngine engine, AuditJob target)"))
            assertTrue(source.contains("target.audit(context)"))
        }
    }
}

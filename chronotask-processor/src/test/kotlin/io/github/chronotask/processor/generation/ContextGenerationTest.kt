package io.github.chronotask.processor.generation

import io.github.chronotask.job.TaskExecutionContext
import io.github.chronotask.processor.TestCompiler
import io.github.chronotask.trigger.ScheduledTrigger
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class ContextGenerationTest {

    @Test
    fun testMethodWithExecutionContext() = runTest {
        TestCompiler.compileAndLoad(
            "com.example.ContextJob" to """
                package com.example;

                import io.github.chronotask.annotation.Scheduled;
                import io.github.chronotask.job.TaskExecutionContext;

                public class ContextJob {
                    public String capturedTaskId = null;

                    @Scheduled(intervalMs = 1000L)
                    public void run(TaskExecutionContext context) {
                        this.capturedTaskId = context.getTaskId();
                    }
                }
            """.trimIndent()
        ).use { env ->
            val targetClass = env.loadClass("com.example.ContextJob")
            val targetInstance = targetClass.getDeclaredConstructor().newInstance()

            val defs = env.getDefinitions("com.example.ContextJob_ChronoTaskBinding", targetInstance)
            assertEquals(1, defs.size)

            val trigger = ScheduledTrigger(
                scheduledAt = Instant.now(),
                discoveredAt = Instant.now()
            )
            val dummyContext = TaskExecutionContext(
                taskId = "test-task-123",
                trigger = trigger,
                attempt = 1
            )

            defs[0].executable.execute(dummyContext)

            val capturedField = targetClass.getField("capturedTaskId")
            assertEquals("test-task-123", capturedField.get(targetInstance))
        }
    }
}

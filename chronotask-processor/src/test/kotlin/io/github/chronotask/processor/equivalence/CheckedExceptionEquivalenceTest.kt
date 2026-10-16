package io.github.chronotask.processor.equivalence

import io.github.chronotask.job.TaskExecutionContext
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.processor.TestCompiler
import io.github.chronotask.trigger.ScheduledTrigger
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.Instant

class CheckedExceptionEquivalenceTest {

    @Suppress("UNCHECKED_CAST")
    private fun scanViaReflection(instance: Any): List<TaskDefinition> {
        val scannerClass = Class.forName("io.github.chronotask.reflect.ReflectionTaskScanner")
        val instanceField = scannerClass.getField("INSTANCE").get(null)
        val scanMethod = scannerClass.getDeclaredMethod("scan", Any::class.java)
        scanMethod.isAccessible = true
        return scanMethod.invoke(instanceField, instance) as List<TaskDefinition>
    }

    @Test
    fun testReflectionAndAptExceptionEquivalence() {
        TestCompiler.compileAndLoad(
            "com.example.FailingJob" to """
                package com.example;

                import io.github.chronotask.annotation.Scheduled;
                import java.io.IOException;

                public class FailingJob {
                    @Scheduled(intervalMs = 1000L)
                    public void fail() throws IOException {
                        throw new IOException("network socket timeout");
                    }
                }
            """.trimIndent()
        ).use { env ->
            val targetClass = env.loadClass("com.example.FailingJob")
            val targetInstance = targetClass.getDeclaredConstructor().newInstance()

            val aptDefs = env.getDefinitions("com.example.FailingJob_ChronoTaskBinding", targetInstance)
            val reflectDefs = scanViaReflection(targetInstance)

            val trigger = ScheduledTrigger(
                scheduledAt = Instant.now(),
                discoveredAt = Instant.now()
            )
            val dummyContext = TaskExecutionContext(
                taskId = "test-task",
                trigger = trigger,
                attempt = 1
            )

            // APT execution
            val aptEx = assertThrows(IOException::class.java) {
                runBlocking {
                    aptDefs[0].executable.execute(dummyContext)
                }
            }
            assertEquals("network socket timeout", aptEx.message)

            // Reflection execution
            val reflectEx = assertThrows(IOException::class.java) {
                runBlocking {
                    reflectDefs[0].executable.execute(dummyContext)
                }
            }
            assertEquals("network socket timeout", reflectEx.message)
        }
    }
}

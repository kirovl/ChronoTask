package io.github.chronotask.processor.equivalence

import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.processor.TestCompiler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReflectionAptEquivalenceTest {

    @Suppress("UNCHECKED_CAST")
    private fun scanViaReflection(instance: Any): List<TaskDefinition> {
        val scannerClass = Class.forName("io.github.chronotask.reflect.ReflectionTaskScanner")
        val instanceField = scannerClass.getField("INSTANCE").get(null)
        val scanMethod = scannerClass.getDeclaredMethod("scan", Any::class.java)
        scanMethod.isAccessible = true
        return scanMethod.invoke(instanceField, instance) as List<TaskDefinition>
    }

    @Test
    fun testReflectionAndAptSemanticEquivalence() {
        TestCompiler.compileAndLoad(
            "com.example.ComprehensiveJob" to """
                package com.example;

                import io.github.chronotask.annotation.*;
                import io.github.chronotask.cron.CronDialect;
                import io.github.chronotask.model.QueueOverflowPolicy;

                public class ComprehensiveJob {
                    @Scheduled(
                        id = "fixed-rate-task",
                        name = "Fixed Rate Task",
                        intervalMs = 5000L,
                        initialDelayMs = 1000L,
                        concurrency = AnnotationConcurrency.ALLOW,
                        misfire = AnnotationMisfire.SKIP,
                        dispatcher = "worker-pool",
                        enabled = true
                    )
                    public void taskFixedRate() {}

                    @Scheduled(
                        id = "fixed-delay-task",
                        intervalMs = 3000L,
                        mode = ScheduleMode.FIXED_DELAY,
                        concurrency = AnnotationConcurrency.SERIAL,
                        serialQueueSize = 256,
                        serialOverflow = QueueOverflowPolicy.DROP_OLDEST,
                        misfire = AnnotationMisfire.CATCH_UP,
                        catchUpLimit = 50
                    )
                    public void taskFixedDelay() {}

                    @Cron(
                        id = "cron-task",
                        expression = "0 0 12 * * ?",
                        zone = "Asia/Tokyo",
                        dialect = CronDialect.QUARTZ,
                        concurrency = AnnotationConcurrency.QUEUE_ONE,
                        misfire = AnnotationMisfire.FIRE_ONCE
                    )
                    public void taskCron() {}

                    @Once(
                        id = "once-task",
                        delayMs = 2000L
                    )
                    public void taskOnce() {}

                    @TickTask(
                        id = "tick-task",
                        intervalMs = 50L
                    )
                    public void taskTick() {}
                }
            """.trimIndent()
        ).use { env ->
            val targetClass = env.loadClass("com.example.ComprehensiveJob")
            val targetInstance = targetClass.getDeclaredConstructor().newInstance()

            val aptDefs = env.getDefinitions("com.example.ComprehensiveJob_ChronoTaskBinding", targetInstance)
            val reflectDefs = scanViaReflection(targetInstance)

            assertEquals(5, aptDefs.size)
            assertEquals(5, reflectDefs.size)

            for (i in aptDefs.indices) {
                val aptDef = aptDefs[i]
                val reflectDef = reflectDefs.first { it.id == aptDef.id }

                assertEquals(reflectDef.id, aptDef.id)
                assertEquals(reflectDef.name, aptDef.name)
                assertEquals(reflectDef.schedule, aptDef.schedule)
                assertEquals(reflectDef.concurrencyPolicy, aptDef.concurrencyPolicy)
                assertEquals(reflectDef.misfirePolicy, aptDef.misfirePolicy)
                assertEquals(reflectDef.dispatcherKey, aptDef.dispatcherKey)
                assertEquals(reflectDef.isEnabled, aptDef.isEnabled)
            }
        }
    }
}

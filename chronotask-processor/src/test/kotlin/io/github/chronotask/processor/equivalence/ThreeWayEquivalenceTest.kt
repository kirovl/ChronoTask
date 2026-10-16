package io.github.chronotask.processor.equivalence

import io.github.chronotask.annotation.AnnotationConcurrency
import io.github.chronotask.annotation.AnnotationMisfire
import io.github.chronotask.annotation.Cron
import io.github.chronotask.annotation.Once
import io.github.chronotask.annotation.ScheduleMode
import io.github.chronotask.annotation.Scheduled
import io.github.chronotask.annotation.TickTask
import io.github.chronotask.cron.CronDialect
import io.github.chronotask.model.QueueOverflowPolicy
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.processor.TestCompiler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ThreeWayEquivalenceTest {

    @Suppress("UNCHECKED_CAST")
    private fun scanViaReflection(instance: Any): List<TaskDefinition> {
        val scannerClass = Class.forName("io.github.chronotask.reflect.ReflectionTaskScanner")
        val instanceField = scannerClass.getField("INSTANCE").get(null)
        val scanMethod = scannerClass.getDeclaredMethod("scan", Any::class.java)
        scanMethod.isAccessible = true
        return scanMethod.invoke(instanceField, instance) as List<TaskDefinition>
    }

    // Kotlin target for Reflection
    class KotlinEquivalenceJob {
        @Scheduled(
            id = "task-fixed-rate",
            name = "Fixed Rate",
            intervalMs = 5000L,
            initialDelayMs = 500L,
            mode = ScheduleMode.FIXED_RATE,
            concurrency = AnnotationConcurrency.ALLOW,
            misfire = AnnotationMisfire.SKIP
        )
        fun runRate() {}

        @Scheduled(
            id = "task-fixed-delay",
            name = "Fixed Delay",
            intervalMs = 3000L,
            initialDelayMs = 0L,
            mode = ScheduleMode.FIXED_DELAY,
            concurrency = AnnotationConcurrency.SERIAL,
            serialQueueSize = 100,
            serialOverflow = QueueOverflowPolicy.DROP_OLDEST,
            misfire = AnnotationMisfire.CATCH_UP,
            catchUpLimit = 25
        )
        fun runDelay() {}

        @Cron(
            id = "task-cron",
            name = "Cron Task",
            expression = "0 0/5 * * * ?",
            zone = "UTC",
            dialect = CronDialect.QUARTZ,
            concurrency = AnnotationConcurrency.QUEUE_ONE,
            misfire = AnnotationMisfire.FIRE_ONCE
        )
        fun runCron() {}

        @Once(
            id = "task-once",
            name = "Once Task",
            delayMs = 1200L
        )
        fun runOnce() {}

        @TickTask(
            id = "task-tick",
            name = "Tick Task",
            intervalMs = 25L
        )
        fun runTick() {}
    }

    @Test
    fun testThreeWaySemanticEquivalence() {
        val kotlinInstance = KotlinEquivalenceJob()
        val reflectDefs = scanViaReflection(kotlinInstance)

        TestCompiler.compileAndLoad(
            "com.example.JavaEquivalenceJob" to """
                package com.example;

                import io.github.chronotask.annotation.*;
                import io.github.chronotask.cron.CronDialect;
                import io.github.chronotask.model.QueueOverflowPolicy;

                public class JavaEquivalenceJob {
                    @Scheduled(
                        id = "task-fixed-rate",
                        name = "Fixed Rate",
                        intervalMs = 5000L,
                        initialDelayMs = 500L,
                        mode = ScheduleMode.FIXED_RATE,
                        concurrency = AnnotationConcurrency.ALLOW,
                        misfire = AnnotationMisfire.SKIP
                    )
                    public void runRate() {}

                    @Scheduled(
                        id = "task-fixed-delay",
                        name = "Fixed Delay",
                        intervalMs = 3000L,
                        initialDelayMs = 0L,
                        mode = ScheduleMode.FIXED_DELAY,
                        concurrency = AnnotationConcurrency.SERIAL,
                        serialQueueSize = 100,
                        serialOverflow = QueueOverflowPolicy.DROP_OLDEST,
                        misfire = AnnotationMisfire.CATCH_UP,
                        catchUpLimit = 25
                    )
                    public void runDelay() {}

                    @Cron(
                        id = "task-cron",
                        name = "Cron Task",
                        expression = "0 0/5 * * * ?",
                        zone = "UTC",
                        dialect = CronDialect.QUARTZ,
                        concurrency = AnnotationConcurrency.QUEUE_ONE,
                        misfire = AnnotationMisfire.FIRE_ONCE
                    )
                    public void runCron() {}

                    @Once(
                        id = "task-once",
                        name = "Once Task",
                        delayMs = 1200L
                    )
                    public void runOnce() {}

                    @TickTask(
                        id = "task-tick",
                        name = "Tick Task",
                        intervalMs = 25L
                    )
                    public void runTick() {}
                }
            """.trimIndent()
        ).use { env ->
            val javaTargetClass = env.loadClass("com.example.JavaEquivalenceJob")
            val javaInstance = javaTargetClass.getDeclaredConstructor().newInstance()

            val aptDefs = env.getDefinitions("com.example.JavaEquivalenceJob_ChronoTaskBinding", javaInstance)

            assertEquals(reflectDefs.size, aptDefs.size)

            for (reflectDef in reflectDefs) {
                val aptDef = aptDefs.first { it.id == reflectDef.id }
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

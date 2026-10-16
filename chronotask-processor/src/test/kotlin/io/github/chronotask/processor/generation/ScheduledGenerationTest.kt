package io.github.chronotask.processor.generation

import io.github.chronotask.model.ConcurrencyPolicy
import io.github.chronotask.model.MisfirePolicy
import io.github.chronotask.model.QueueOverflowPolicy
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.processor.TestCompiler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScheduledGenerationTest {

    @Test
    fun testDefaultScheduledFixedRate() {
        TestCompiler.compileAndLoad(
            "com.example.ScheduledDefaultJob" to """
                package com.example;

                import io.github.chronotask.annotation.Scheduled;

                public class ScheduledDefaultJob {
                    @Scheduled(intervalMs = 5000L)
                    public void run() {}
                }
            """.trimIndent()
        ).use { env ->
            val targetClass = env.loadClass("com.example.ScheduledDefaultJob")
            val targetInstance = targetClass.getDeclaredConstructor().newInstance()

            val defs = env.getDefinitions("com.example.ScheduledDefaultJob_ChronoTaskBinding", targetInstance)
            assertEquals(1, defs.size)

            val def = defs[0]
            assertEquals("com.example.ScheduledDefaultJob#run", def.id)
            assertEquals("com.example.ScheduledDefaultJob#run", def.name)
            assertEquals(TaskSchedule.FixedRate(5000L, 0L), def.schedule)
            assertEquals(ConcurrencyPolicy.SkipIfRunning, def.concurrencyPolicy)
            assertEquals(MisfirePolicy.Skip, def.misfirePolicy)
            assertNull(def.dispatcherKey)
            assertTrue(def.isEnabled)
        }
    }

    @Test
    fun testScheduledFixedDelayWithCustomOptions() {
        TestCompiler.compileAndLoad(
            "com.example.ScheduledCustomJob" to """
                package com.example;

                import io.github.chronotask.annotation.AnnotationConcurrency;
                import io.github.chronotask.annotation.AnnotationMisfire;
                import io.github.chronotask.annotation.ScheduleMode;
                import io.github.chronotask.annotation.Scheduled;
                import io.github.chronotask.model.QueueOverflowPolicy;

                public class ScheduledCustomJob {
                    @Scheduled(
                        id = "custom-id",
                        name = "Custom Task Name",
                        intervalMs = 3000L,
                        initialDelayMs = 1000L,
                        mode = ScheduleMode.FIXED_DELAY,
                        concurrency = AnnotationConcurrency.SERIAL,
                        serialQueueSize = 50,
                        serialOverflow = QueueOverflowPolicy.DROP_LATEST,
                        misfire = AnnotationMisfire.CATCH_UP,
                        catchUpLimit = 20,
                        dispatcher = "io-dispatcher",
                        enabled = false
                    )
                    public void doWork() {}
                }
            """.trimIndent()
        ).use { env ->
            val targetClass = env.loadClass("com.example.ScheduledCustomJob")
            val targetInstance = targetClass.getDeclaredConstructor().newInstance()

            val defs = env.getDefinitions("com.example.ScheduledCustomJob_ChronoTaskBinding", targetInstance)
            assertEquals(1, defs.size)

            val def = defs[0]
            assertEquals("custom-id", def.id)
            assertEquals("Custom Task Name", def.name)
            assertEquals(TaskSchedule.FixedDelay(3000L, 1000L), def.schedule)
            assertEquals(ConcurrencyPolicy.Serial(50, QueueOverflowPolicy.DROP_LATEST), def.concurrencyPolicy)
            assertEquals(MisfirePolicy.CatchUp(20), def.misfirePolicy)
            assertEquals("io-dispatcher", def.dispatcherKey)
            assertFalse(def.isEnabled)
        }
    }

    @Test
    fun testPackagePrivateMethodSupported() {
        TestCompiler.compileAndLoad(
            "com.example.PackagePrivateJob" to """
                package com.example;

                import io.github.chronotask.annotation.Scheduled;

                public class PackagePrivateJob {
                    @Scheduled(intervalMs = 2000L)
                    void packagePrivateRun() {}
                }
            """.trimIndent()
        ).use { env ->
            val targetClass = env.loadClass("com.example.PackagePrivateJob")
            val targetInstance = targetClass.getDeclaredConstructor().newInstance()

            val defs = env.getDefinitions("com.example.PackagePrivateJob_ChronoTaskBinding", targetInstance)
            assertEquals(1, defs.size)
            assertEquals("com.example.PackagePrivateJob#packagePrivateRun", defs[0].id)
        }
    }
}

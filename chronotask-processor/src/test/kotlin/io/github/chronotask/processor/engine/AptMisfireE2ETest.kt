package io.github.chronotask.processor.engine

import io.github.chronotask.ChronoTask
import io.github.chronotask.engine.DefaultDispatcherProvider
import io.github.chronotask.engine.EngineConfig
import io.github.chronotask.processor.TestCompiler
import io.github.chronotask.test.time.VirtualTimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class AptMisfireE2ETest {

    private val baseTime = Instant.parse("2026-01-01T10:00:00Z")

    @Test
    fun testAptMisfireCatchUpExecutesMissedTriggers() = runBlocking {
        TestCompiler.compileAndLoad(
            "com.example.MisfireJob" to """
                package com.example;

                import io.github.chronotask.annotation.AnnotationMisfire;
                import io.github.chronotask.annotation.Scheduled;
                import java.util.concurrent.atomic.AtomicInteger;

                public class MisfireJob {
                    public final AtomicInteger executions = new AtomicInteger(0);

                    @Scheduled(
                        id = "apt-catchup-task",
                        intervalMs = 1000L,
                        initialDelayMs = 1000L,
                        misfire = AnnotationMisfire.CATCH_UP,
                        catchUpLimit = 10
                    )
                    public void onSchedule() {
                        executions.incrementAndGet();
                    }
                }
            """.trimIndent()
        ).use { env ->
            val timeSource = VirtualTimeSource(baseTime)
            val config = EngineConfig(
                clock = timeSource.clock,
                timer = timeSource.timer,
                dispatcherProvider = DefaultDispatcherProvider(defaultDispatcher = Dispatchers.Unconfined)
            )
            val engine = ChronoTask.create(config)

            val jobClass = env.loadClass("com.example.MisfireJob")
            val jobInstance = jobClass.getDeclaredConstructor().newInstance()

            env.register("com.example.MisfireJob_ChronoTaskBinding", engine, jobInstance)

            engine.start()

            val executionsField = jobClass.getField("executions")
            val executions = executionsField.get(jobInstance) as java.util.concurrent.atomic.AtomicInteger

            // Advance time significantly in one step (simulating a freeze or pause)
            timeSource.advanceBy(Duration.ofSeconds(5))
            assertEquals(5, executions.get())

            engine.stop()
        }
    }
}

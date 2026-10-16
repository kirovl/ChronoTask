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

class AptConcurrencyE2ETest {

    private val baseTime = Instant.parse("2026-01-01T10:00:00Z")

    @Test
    fun testAptConcurrencySkipIfRunning() = runBlocking {
        TestCompiler.compileAndLoad(
            "com.example.ConcurrencyJob" to """
                package com.example;

                import io.github.chronotask.annotation.AnnotationConcurrency;
                import io.github.chronotask.annotation.Scheduled;
                import java.util.concurrent.atomic.AtomicInteger;

                public class ConcurrencyJob {
                    public final AtomicInteger totalRuns = new AtomicInteger(0);

                    @Scheduled(
                        id = "apt-skip-task",
                        intervalMs = 1000L,
                        initialDelayMs = 1000L,
                        concurrency = AnnotationConcurrency.SKIP_IF_RUNNING
                    )
                    public void onRun() {
                        totalRuns.incrementAndGet();
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

            val jobClass = env.loadClass("com.example.ConcurrencyJob")
            val jobInstance = jobClass.getDeclaredConstructor().newInstance()

            val handles = env.register("com.example.ConcurrencyJob_ChronoTaskBinding", engine, jobInstance)
            assertEquals(1, handles.size)

            engine.start()

            val totalRunsField = jobClass.getField("totalRuns")
            val totalRuns = totalRunsField.get(jobInstance) as java.util.concurrent.atomic.AtomicInteger

            timeSource.advanceBy(Duration.ofSeconds(3))
            assertEquals(3, totalRuns.get())

            engine.stop()
        }
    }
}

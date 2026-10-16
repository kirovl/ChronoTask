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

class AptFixedRateE2ETest {

    private val baseTime = Instant.parse("2026-01-01T10:00:00Z")

    @Test
    fun testAptFixedRateSchedulesAcrossVirtualTime() = runBlocking {
        TestCompiler.compileAndLoad(
            "com.example.FixedRateJob" to """
                package com.example;

                import io.github.chronotask.annotation.Scheduled;
                import io.github.chronotask.job.TaskExecutionContext;
                import java.time.Instant;
                import java.util.ArrayList;
                import java.util.List;

                public class FixedRateJob {
                    public final List<Instant> timestamps = new ArrayList<>();

                    @Scheduled(id = "apt-rate-task", intervalMs = 5000L, initialDelayMs = 5000L)
                    public void onRate(TaskExecutionContext ctx) {
                        timestamps.add(ctx.getTrigger().getScheduledAt());
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

            val jobClass = env.loadClass("com.example.FixedRateJob")
            val jobInstance = jobClass.getDeclaredConstructor().newInstance()

            env.register("com.example.FixedRateJob_ChronoTaskBinding", engine, jobInstance)

            engine.start()

            timeSource.advanceBy(Duration.ofSeconds(4))
            val timestampsField = jobClass.getField("timestamps")
            @Suppress("UNCHECKED_CAST")
            val timestamps = timestampsField.get(jobInstance) as List<Instant>

            assertEquals(0, timestamps.size)

            timeSource.advanceBy(Duration.ofSeconds(1)) // 10:00:05
            assertEquals(listOf(Instant.parse("2026-01-01T10:00:05Z")), timestamps)

            timeSource.advanceBy(Duration.ofSeconds(5)) // 10:00:10
            assertEquals(
                listOf(
                    Instant.parse("2026-01-01T10:00:05Z"),
                    Instant.parse("2026-01-01T10:00:10Z")
                ),
                timestamps
            )

            engine.stop()
        }
    }
}

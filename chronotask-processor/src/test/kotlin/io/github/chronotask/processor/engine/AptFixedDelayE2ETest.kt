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

class AptFixedDelayE2ETest {

    private val baseTime = Instant.parse("2026-01-01T10:00:00Z")

    @Test
    fun testAptFixedDelaySchedulesAfterExecutionCompletion() = runBlocking {
        TestCompiler.compileAndLoad(
            "com.example.FixedDelayJob" to """
                package com.example;

                import io.github.chronotask.annotation.ScheduleMode;
                import io.github.chronotask.annotation.Scheduled;
                import java.util.concurrent.atomic.AtomicInteger;

                public class FixedDelayJob {
                    public final AtomicInteger count = new AtomicInteger(0);

                    @Scheduled(
                        id = "apt-delay-task",
                        intervalMs = 3000L,
                        initialDelayMs = 2000L,
                        mode = ScheduleMode.FIXED_DELAY
                    )
                    public void onDelay() {
                        count.incrementAndGet();
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

            val jobClass = env.loadClass("com.example.FixedDelayJob")
            val jobInstance = jobClass.getDeclaredConstructor().newInstance()

            env.register("com.example.FixedDelayJob_ChronoTaskBinding", engine, jobInstance)

            engine.start()

            val countField = jobClass.getField("count")
            val count = countField.get(jobInstance) as java.util.concurrent.atomic.AtomicInteger

            timeSource.advanceBy(Duration.ofSeconds(1))
            assertEquals(0, count.get())

            timeSource.advanceBy(Duration.ofSeconds(1)) // 10:00:02 (initial delay triggers)
            assertEquals(1, count.get())

            timeSource.advanceBy(Duration.ofSeconds(3)) // 10:00:05 (next delay triggers)
            assertEquals(2, count.get())

            timeSource.advanceBy(Duration.ofSeconds(3)) // 10:00:08 (next delay triggers)
            assertEquals(3, count.get())

            engine.stop()
        }
    }
}

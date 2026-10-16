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
import java.util.concurrent.atomic.AtomicInteger

class AptLifecycleE2ETest {

    private val baseTime = Instant.parse("2026-01-01T10:00:00Z")

    @Test
    fun testAptPauseResumeAndUnregister() = runBlocking {
        TestCompiler.compileAndLoad(
            "com.example.LifecycleJob" to """
                package com.example;

                import io.github.chronotask.annotation.Scheduled;
                import java.util.concurrent.atomic.AtomicInteger;

                public class LifecycleJob {
                    public final AtomicInteger count = new AtomicInteger(0);

                    @Scheduled(id = "lifecycle-task", intervalMs = 1000L, initialDelayMs = 1000L)
                    public void tick() {
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

            val jobClass = env.loadClass("com.example.LifecycleJob")
            val jobInstance = jobClass.getDeclaredConstructor().newInstance()

            val handles = env.register("com.example.LifecycleJob_ChronoTaskBinding", engine, jobInstance)
            assertEquals(1, handles.size)

            val handle = handles[0]
            val countField = jobClass.getField("count")
            val count = countField.get(jobInstance) as AtomicInteger

            engine.start()

            timeSource.advanceBy(Duration.ofSeconds(1)) // 10:00:01
            assertEquals(1, count.get())
            timeSource.advanceBy(Duration.ofSeconds(1)) // 10:00:02
            assertEquals(2, count.get())

            // Pause
            handle.pause()
            timeSource.advanceBy(Duration.ofSeconds(5))
            assertEquals(2, count.get()) // No new executions while paused

            // Resume -> immediate resume trigger
            handle.resume()
            assertEquals(3, count.get())

            // Advance by 1s
            timeSource.advanceBy(Duration.ofSeconds(1))
            assertEquals(4, count.get())

            // Cancel via handle
            handle.cancel()
            timeSource.advanceBy(Duration.ofSeconds(10))
            assertEquals(4, count.get())

            engine.stop()
        }
    }
}

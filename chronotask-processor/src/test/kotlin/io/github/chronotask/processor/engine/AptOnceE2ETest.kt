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

class AptOnceE2ETest {

    private val baseTime = Instant.parse("2026-01-01T10:00:00Z")

    @Test
    fun testAptOnceExecutesSingleTime() = runBlocking {
        TestCompiler.compileAndLoad(
            "com.example.OnceExecutionJob" to """
                package com.example;

                import io.github.chronotask.annotation.Once;
                import java.util.concurrent.atomic.AtomicInteger;

                public class OnceExecutionJob {
                    public final AtomicInteger count = new AtomicInteger(0);

                    @Once(id = "apt-once-task", delayMs = 3000L)
                    public void fireOnce() {
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

            val jobClass = env.loadClass("com.example.OnceExecutionJob")
            val jobInstance = jobClass.getDeclaredConstructor().newInstance()

            env.register("com.example.OnceExecutionJob_ChronoTaskBinding", engine, jobInstance)

            engine.start()

            val countField = jobClass.getField("count")
            val count = countField.get(jobInstance) as java.util.concurrent.atomic.AtomicInteger

            timeSource.advanceBy(Duration.ofSeconds(2))
            assertEquals(0, count.get())

            timeSource.advanceBy(Duration.ofSeconds(1)) // 10:00:03 -> fires
            assertEquals(1, count.get())

            timeSource.advanceBy(Duration.ofSeconds(10)) // Should not fire again
            assertEquals(1, count.get())

            engine.stop()
        }
    }
}

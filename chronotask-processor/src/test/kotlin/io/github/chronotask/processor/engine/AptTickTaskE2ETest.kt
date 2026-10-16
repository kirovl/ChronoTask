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

class AptTickTaskE2ETest {

    private val baseTime = Instant.parse("2026-01-01T10:00:00Z")

    @Test
    fun testAptTickTaskFiresAtHighFrequency() = runBlocking {
        TestCompiler.compileAndLoad(
            "com.example.TickE2EJob" to """
                package com.example;

                import io.github.chronotask.annotation.TickTask;
                import java.util.concurrent.atomic.AtomicInteger;

                public class TickE2EJob {
                    public final AtomicInteger ticks = new AtomicInteger(0);

                    @TickTask(id = "apt-tick-task", intervalMs = 100L, initialDelayMs = 100L)
                    public void onTick() {
                        ticks.incrementAndGet();
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

            val jobClass = env.loadClass("com.example.TickE2EJob")
            val jobInstance = jobClass.getDeclaredConstructor().newInstance()

            env.register("com.example.TickE2EJob_ChronoTaskBinding", engine, jobInstance)

            engine.start()

            val ticksField = jobClass.getField("ticks")
            val ticks = ticksField.get(jobInstance) as java.util.concurrent.atomic.AtomicInteger

            timeSource.advanceBy(Duration.ofMillis(500))
            assertEquals(5, ticks.get())

            timeSource.advanceBy(Duration.ofMillis(500))
            assertEquals(10, ticks.get())

            engine.stop()
        }
    }
}

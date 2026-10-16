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
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

class AptTriggerNowE2ETest {

    private val baseTime = Instant.parse("2026-01-01T10:00:00Z")

    @Test
    fun testAptManualTriggerNow() = runBlocking {
        TestCompiler.compileAndLoad(
            "com.example.ManualTriggerJob" to """
                package com.example;

                import io.github.chronotask.annotation.Scheduled;
                import java.util.concurrent.atomic.AtomicInteger;

                public class ManualTriggerJob {
                    public final AtomicInteger count = new AtomicInteger(0);

                    @Scheduled(id = "manual-task", intervalMs = 60000L, initialDelayMs = 60000L)
                    public void run() {
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

            val jobClass = env.loadClass("com.example.ManualTriggerJob")
            val jobInstance = jobClass.getDeclaredConstructor().newInstance()

            val handles = env.register("com.example.ManualTriggerJob_ChronoTaskBinding", engine, jobInstance)
            assertEquals(1, handles.size)

            val handle = handles[0]
            val countField = jobClass.getField("count")
            val count = countField.get(jobInstance) as AtomicInteger

            assertEquals(0, count.get())

            // Manually trigger task before engine start
            handle.triggerNow()
            assertEquals(1, count.get())

            engine.start()

            handle.triggerNow()
            assertEquals(2, count.get())

            engine.stop()
        }
    }
}

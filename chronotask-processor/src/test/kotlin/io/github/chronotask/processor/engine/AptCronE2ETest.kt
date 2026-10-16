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

class AptCronE2ETest {

    private val baseTime = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun testAptCronTriggersOnCronSchedule() = runBlocking {
        TestCompiler.compileAndLoad(
            "com.example.CronJob" to """
                package com.example;

                import io.github.chronotask.annotation.Cron;
                import io.github.chronotask.cron.CronDialect;
                import java.util.concurrent.atomic.AtomicInteger;

                public class CronJob {
                    public final AtomicInteger count = new AtomicInteger(0);

                    @Cron(
                        id = "apt-cron-hourly",
                        expression = "0 0 * * * ?",
                        zone = "UTC",
                        dialect = CronDialect.QUARTZ
                    )
                    public void hourly() {
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

            val jobClass = env.loadClass("com.example.CronJob")
            val jobInstance = jobClass.getDeclaredConstructor().newInstance()

            env.register("com.example.CronJob_ChronoTaskBinding", engine, jobInstance)

            engine.start()

            val countField = jobClass.getField("count")
            val count = countField.get(jobInstance) as java.util.concurrent.atomic.AtomicInteger

            timeSource.advanceBy(Duration.ofMinutes(59))
            assertEquals(0, count.get())

            timeSource.advanceBy(Duration.ofMinutes(1)) // 01:00:00
            assertEquals(1, count.get())

            timeSource.advanceBy(Duration.ofHours(1)) // 02:00:00
            assertEquals(2, count.get())

            engine.stop()
        }
    }
}

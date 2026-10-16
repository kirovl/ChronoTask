package io.github.chronotask.processor.engine

import io.github.chronotask.ChronoTask
import io.github.chronotask.engine.DefaultDispatcherProvider
import io.github.chronotask.engine.DuplicateTaskException
import io.github.chronotask.engine.EngineConfig
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.processor.TestCompiler
import io.github.chronotask.test.time.VirtualTimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationTargetException
import java.time.Instant

class AptRollbackTest {

    private val baseTime = Instant.parse("2026-01-01T10:00:00Z")

    @Test
    fun testRollbackWhenRegistrationFailsPartially() = runBlocking {
        TestCompiler.compileAndLoad(
            "com.example.BatchJob" to """
                package com.example;

                import io.github.chronotask.annotation.Scheduled;

                public class BatchJob {
                    @Scheduled(id = "batch-task-1", intervalMs = 1000L)
                    public void task1() {}

                    @Scheduled(id = "batch-task-2", intervalMs = 1000L)
                    public void task2() {}
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

            // Pre-register batch-task-2 so that registering BatchJob will fail on second task
            engine.register(
                TaskDefinition(
                    id = "batch-task-2",
                    schedule = TaskSchedule.FixedRate(5000L),
                    executable = { }
                )
            )

            val jobClass = env.loadClass("com.example.BatchJob")
            val jobInstance = jobClass.getDeclaredConstructor().newInstance()

            // Registration should throw DuplicateTaskException (or InvocationTargetException wrapping it)
            val ex = assertThrows(InvocationTargetException::class.java) {
                env.register("com.example.BatchJob_ChronoTaskBinding", engine, jobInstance)
            }
            val rootCause = ex.targetException
            assertTrue(rootCause is DuplicateTaskException)

            // Verify batch-task-1 was rolled back and is not registered in engine
            assertNull(engine.getHandle("batch-task-1"))

            engine.stop()
        }
    }
}

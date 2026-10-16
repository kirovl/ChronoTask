package io.github.chronotask.processor.engine

import io.github.chronotask.ChronoTask
import io.github.chronotask.engine.ChronoTaskEngine
import io.github.chronotask.engine.EngineConfig
import io.github.chronotask.engine.TaskHandle
import io.github.chronotask.model.EngineState
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.processor.TestCompiler
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationTargetException
import java.util.Collections

class AptRollbackOrderTest {

    @Test
    fun testRollbackExecutesInReverseOrder() = runBlocking {
        TestCompiler.compileAndLoad(
            "com.example.OrderJob" to """
                package com.example;

                import io.github.chronotask.annotation.Scheduled;

                public class OrderJob {
                    @Scheduled(id = "task-a", intervalMs = 1000L)
                    public void taskA() {}

                    @Scheduled(id = "task-b", intervalMs = 1000L)
                    public void taskB() {}

                    @Scheduled(id = "task-c", intervalMs = 1000L)
                    public void taskC() {}
                }
            """.trimIndent()
        ).use { env ->
            val realEngine = ChronoTask.create()
            val unregisteredList = Collections.synchronizedList(mutableListOf<String>())

            val spyEngine = object : ChronoTaskEngine {
                override val state: EngineState get() = realEngine.state

                override fun register(definition: TaskDefinition): TaskHandle {
                    if (definition.id == "task-c") {
                        throw IllegalStateException("Simulated failure on task-c")
                    }
                    val handle = realEngine.register(definition)
                    return object : TaskHandle by handle {
                        override fun unregister(): Boolean {
                            unregisteredList.add(handle.taskId)
                            return handle.unregister()
                        }
                    }
                }

                override fun unregister(taskId: String): Boolean {
                    unregisteredList.add(taskId)
                    return realEngine.unregister(taskId)
                }

                override fun getHandle(taskId: String): TaskHandle? = realEngine.getHandle(taskId)
                override fun start() = realEngine.start()
                override fun pause() = realEngine.pause()
                override fun resume() = realEngine.resume()
                override fun stop() = realEngine.stop()
                override fun close() = realEngine.close()
            }

            val jobClass = env.loadClass("com.example.OrderJob")
            val jobInstance = jobClass.getDeclaredConstructor().newInstance()

            val ex = assertThrows(InvocationTargetException::class.java) {
                env.register("com.example.OrderJob_ChronoTaskBinding", spyEngine, jobInstance)
            }
            val rootCause = ex.targetException
            assertTrue(rootCause is IllegalStateException)
            assertEquals("Simulated failure on task-c", rootCause.message)

            // Assert LIFO / Reverse registration order rollback: task-b, then task-a
            assertEquals(listOf("task-b", "task-a"), unregisteredList)

            realEngine.stop()
        }
    }

    @Test
    fun testRollbackCollectsSuppressedExceptionsAndContinues() = runBlocking {
        TestCompiler.compileAndLoad(
            "com.example.SuppressedOrderJob" to """
                package com.example;

                import io.github.chronotask.annotation.Scheduled;

                public class SuppressedOrderJob {
                    @Scheduled(id = "task-1", intervalMs = 1000L)
                    public void task1() {}

                    @Scheduled(id = "task-2", intervalMs = 1000L)
                    public void task2() {}

                    @Scheduled(id = "task-3", intervalMs = 1000L)
                    public void task3() {}
                }
            """.trimIndent()
        ).use { env ->
            val realEngine = ChronoTask.create()
            val unregisteredList = Collections.synchronizedList(mutableListOf<String>())

            val rollbackError = RuntimeException("Rollback error on task-2")

            val spyEngine = object : ChronoTaskEngine {
                override val state: EngineState get() = realEngine.state

                override fun register(definition: TaskDefinition): TaskHandle {
                    if (definition.id == "task-3") {
                        throw IllegalStateException("Simulated failure on task-3")
                    }
                    val handle = realEngine.register(definition)
                    return object : TaskHandle by handle {
                        override fun unregister(): Boolean {
                            unregisteredList.add(handle.taskId)
                            if (handle.taskId == "task-2") throw rollbackError
                            return handle.unregister()
                        }
                    }
                }

                override fun unregister(taskId: String): Boolean {
                    unregisteredList.add(taskId)
                    if (taskId == "task-2") {
                        throw rollbackError
                    }
                    return realEngine.unregister(taskId)
                }

                override fun getHandle(taskId: String): TaskHandle? = realEngine.getHandle(taskId)
                override fun start() = realEngine.start()
                override fun pause() = realEngine.pause()
                override fun resume() = realEngine.resume()
                override fun stop() = realEngine.stop()
                override fun close() = realEngine.close()
            }

            val jobClass = env.loadClass("com.example.SuppressedOrderJob")
            val jobInstance = jobClass.getDeclaredConstructor().newInstance()

            val ex = assertThrows(InvocationTargetException::class.java) {
                env.register("com.example.SuppressedOrderJob_ChronoTaskBinding", spyEngine, jobInstance)
            }
            val rootCause = ex.targetException
            assertTrue(rootCause is IllegalStateException)
            assertEquals("Simulated failure on task-3", rootCause.message)

            // Verify suppressed exception was added to root cause
            assertEquals(1, rootCause.suppressed.size)
            assertEquals("Rollback error on task-2", rootCause.suppressed[0].message)

            // Verify that task-1 was STILL rolled back after task-2 threw
            assertEquals(listOf("task-2", "task-1"), unregisteredList)

            realEngine.stop()
        }
    }
}

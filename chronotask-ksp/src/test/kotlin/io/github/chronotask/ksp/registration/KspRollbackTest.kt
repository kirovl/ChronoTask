package io.github.chronotask.ksp.registration

import io.github.chronotask.ChronoTask
import io.github.chronotask.annotation.Scheduled
import io.github.chronotask.engine.DuplicateTaskException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RollbackTarget {
    @Scheduled(intervalMs = 1000, id = "rb-first-task")
    fun first() {}

    @Scheduled(intervalMs = 2000, id = "rb-conflicting-task")
    fun second() {}
}

class RollbackOrderTarget {
    @Scheduled(intervalMs = 1000, id = "ksp-order-a")
    fun a() {}

    @Scheduled(intervalMs = 1000, id = "ksp-order-b")
    fun b() {}

    @Scheduled(intervalMs = 1000, id = "ksp-order-c")
    fun c() {}
}

class KspRollbackTest {

    private val engine = ChronoTask.create()

    @BeforeEach
    fun setUp() {
        engine.start()
    }

    @AfterEach
    fun tearDown() {
        engine.stop()
    }

    @Test
    fun `registration failure on subsequent task rolls back previous tasks in batch`() {
        // Pre-register rb-conflicting-task into engine to cause a conflict on the second task
        val dummy = ChronoTask.newTask("rb-conflicting-task")
            .fixedRate(java.time.Duration.ofSeconds(10))
            .executeRunnable {}
            .build()
        engine.register(dummy)

        val target = RollbackTarget()
        assertThrows(DuplicateTaskException::class.java) {
            RollbackTarget_ChronoTaskBinding.register(engine, target)
        }

        // rb-first-task must have been rolled back and unregistered!
        assertNull(engine.getHandle("rb-first-task"))
    }

    @Test
    fun `rollback executes in exact reverse order`() {
        val unregistered = mutableListOf<String>()
        val realEngine = ChronoTask.create()
        val spyEngine = object : io.github.chronotask.engine.ChronoTaskEngine {
            override val state get() = realEngine.state

            override fun register(definition: io.github.chronotask.model.TaskDefinition): io.github.chronotask.engine.TaskHandle {
                if (definition.id == "ksp-order-c") {
                    throw IllegalStateException("Failure on task C")
                }
                val handle = realEngine.register(definition)
                return object : io.github.chronotask.engine.TaskHandle by handle {
                    override fun unregister(): Boolean {
                        unregistered.add(handle.taskId)
                        return handle.unregister()
                    }
                }
            }

            override fun unregister(taskId: String): Boolean {
                unregistered.add(taskId)
                return realEngine.unregister(taskId)
            }

            override fun getHandle(taskId: String) = realEngine.getHandle(taskId)
            override fun start() = realEngine.start()
            override fun pause() = realEngine.pause()
            override fun resume() = realEngine.resume()
            override fun stop() = realEngine.stop()
            override fun close() = realEngine.close()
        }

        val ex = assertThrows(IllegalStateException::class.java) {
            RollbackOrderTarget_ChronoTaskBinding.register(spyEngine, RollbackOrderTarget())
        }
        assertEquals("Failure on task C", ex.message)
        assertEquals(listOf("ksp-order-b", "ksp-order-a"), unregistered)
        realEngine.stop()
    }
}

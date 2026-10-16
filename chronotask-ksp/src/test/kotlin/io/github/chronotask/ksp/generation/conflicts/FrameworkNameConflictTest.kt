package io.github.chronotask.ksp.generation.conflicts

import io.github.chronotask.annotation.Once
import io.github.chronotask.engine.DefaultChronoTaskEngine
import io.github.chronotask.engine.DefaultDispatcherProvider
import io.github.chronotask.engine.EngineConfig
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TaskHandle {
    var calls = 0
    @Once(id = "handle-name", enabled = false) fun run() { calls++ }
}

class FrameworkTypeParameterTarget<TaskDefinition, TaskHandle, TaskExecutable, TaskSchedule, ChronoTaskEngine,
    ArrayList, List, Throwable> {
    var calls = 0
    @Once(id = "type-parameter-names", enabled = false) fun run() { calls++ }
}

class BoundNameTarget<Number : kotlin.Number>(val value: Number) {
    var calls = 0
    @Once(id = "bound-name", enabled = false) fun run() { calls++ }
}

class FrameworkNameConflictTest {
    @Test fun `type parameter names do not shadow framework classes`() {
        val target = FrameworkTypeParameterTarget<String, String, String, String, String, String, String, String>()
        DefaultChronoTaskEngine(EngineConfig(
            dispatcherProvider = DefaultDispatcherProvider(Dispatchers.Unconfined))).use { engine ->
            val handle = FrameworkTypeParameterTarget_ChronoTaskBinding.register(engine, target).single()
            handle.triggerNow()
            assertEquals(1, target.calls)
            assertEquals(true, handle.unregister())
        }
    }

    @Test fun `type parameter names do not shadow their upper bound class`() {
        val target = BoundNameTarget(42)
        DefaultChronoTaskEngine(EngineConfig(
            dispatcherProvider = DefaultDispatcherProvider(Dispatchers.Unconfined))).use { engine ->
            BoundNameTarget_ChronoTaskBinding.register(engine, target).single().triggerNow()
            assertEquals(1, target.calls)
        }
    }

    @Test fun `task target named TaskHandle registers using engine handle type`() {
        val target = TaskHandle()
        DefaultChronoTaskEngine(EngineConfig(
            dispatcherProvider = DefaultDispatcherProvider(Dispatchers.Unconfined))).use { engine ->
            val handle = TaskHandle_ChronoTaskBinding.register(engine, target).single()
            handle.triggerNow()
            assertEquals(1, target.calls)
            assertEquals(true, handle.unregister())
        }
    }
}

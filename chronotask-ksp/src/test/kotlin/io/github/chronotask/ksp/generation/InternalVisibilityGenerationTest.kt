package io.github.chronotask.ksp.generation

import io.github.chronotask.annotation.Once
import io.github.chronotask.engine.DefaultChronoTaskEngine
import io.github.chronotask.engine.DefaultDispatcherProvider
import io.github.chronotask.engine.EngineConfig
import io.github.chronotask.test.time.VirtualTimeSource
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class InternalJob {
    var calls = 0
    @Once(id = "internal-job", enabled = false)
    internal fun run() { calls++ }
}

internal class InternalContainer {
    class NestedJob {
        var calls = 0
        @Once(id = "nested-internal-job", enabled = false)
        fun run() { calls++ }
    }
}

internal object InternalObjectJob {
    @Once(id = "internal-object", enabled = false)
    fun run() {}
}

class InternalVisibilityGenerationTest {
    @Test fun internalJobCanBeRegisteredAndExecutedWithinItsModule() {
        val time = VirtualTimeSource()
        val engine = DefaultChronoTaskEngine(EngineConfig(clock = time.clock, timer = time.timer,
            dispatcherProvider = DefaultDispatcherProvider(Dispatchers.Unconfined)))
        try {
            val job = InternalJob()
            val handle = InternalJob_ChronoTaskBinding.register(engine, job).single()
            assertEquals("internal-job", handle.taskId)
            handle.triggerNow()
            assertEquals(1, job.calls)
        } finally { engine.stop() }
    }

    @Test fun publicNestedJobInInternalContainerCanBeExecuted() {
        val time = VirtualTimeSource()
        val engine = DefaultChronoTaskEngine(EngineConfig(clock = time.clock, timer = time.timer,
            dispatcherProvider = DefaultDispatcherProvider(Dispatchers.Unconfined)))
        try {
            val job = InternalContainer.NestedJob()
            val handle = InternalContainer_NestedJob_ChronoTaskBinding.register(engine, job).single()
            handle.triggerNow()
            assertEquals(1, job.calls)
        } finally { engine.stop() }
    }

    @Test fun internalObjectHasUsableDefinitions() {
        assertEquals("internal-object", InternalObjectJob_ChronoTaskBinding.definitions(InternalObjectJob).single().id)
    }
}

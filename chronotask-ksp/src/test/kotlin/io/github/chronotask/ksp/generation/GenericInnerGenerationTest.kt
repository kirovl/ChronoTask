package io.github.chronotask.ksp.generation

import io.github.chronotask.annotation.Once
import io.github.chronotask.engine.DefaultChronoTaskEngine
import io.github.chronotask.engine.DefaultDispatcherProvider
import io.github.chronotask.engine.EngineConfig
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GenericInnerOuter<T>(val value: T) {
    val calls = mutableListOf<String>()
    inner class Job {
        @Once(id = "inner-job", enabled = false)
        fun run() { calls += value.toString() }
    }
    inner class BoundedJob<U : T>(val ownValue: U) {
        @Once(id = "bounded-inner-job", enabled = false)
        suspend fun run() { calls += "$value:$ownValue" }
    }
    inner class Middle<U>(val middleValue: U) {
        inner class Job {
            @Once(id = "deep-inner-job", enabled = false)
            fun run() { calls += "$value:$middleValue" }
        }
    }
    class StaticContainer<V>(val value: V) {
        inner class Job {
            @Once(id = "static-inner-job", enabled = false)
            fun run() { calls++ }
        }
        var calls = 0
    }
}

class GenericInnerGenerationTest {
    private fun engine() = DefaultChronoTaskEngine(EngineConfig(
        dispatcherProvider = DefaultDispatcherProvider(Dispatchers.Unconfined)))

    @Test fun `inner task captures generic outer instance`() {
        val outer = GenericInnerOuter("outer")
        engine().use { engine ->
            GenericInnerOuter_Job_ChronoTaskBinding.register(engine, outer.Job()).single().triggerNow()
            assertEquals(listOf("outer"), outer.calls)
        }
    }

    @Test fun `generic inner suspend task can reference outer bound`() {
        val outer = GenericInnerOuter<CharSequence>("outer")
        engine().use { engine ->
            GenericInnerOuter_BoundedJob_ChronoTaskBinding.register(engine, outer.BoundedJob("own"))
                .single().triggerNow()
            assertEquals(listOf("outer:own"), outer.calls)
        }
    }

    @Test fun `nested inner tasks retain all enclosing generic types`() {
        val outer = GenericInnerOuter("outer")
        val middle = outer.Middle(42)
        engine().use { engine ->
            GenericInnerOuter_Middle_Job_ChronoTaskBinding.register(engine, middle.Job()).single().triggerNow()
            assertEquals(listOf("outer:42"), outer.calls)
        }
    }

    @Test fun `static nesting does not capture unrelated outer type parameters`() {
        val container = GenericInnerOuter.StaticContainer(42)
        engine().use { engine ->
            GenericInnerOuter_StaticContainer_Job_ChronoTaskBinding.register(engine, container.Job())
                .single().triggerNow()
            assertEquals(1, container.calls)
        }
    }
}

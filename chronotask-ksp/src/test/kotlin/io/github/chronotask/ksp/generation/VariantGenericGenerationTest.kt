package io.github.chronotask.ksp.generation

import io.github.chronotask.annotation.Once
import io.github.chronotask.engine.DefaultChronoTaskEngine
import io.github.chronotask.engine.DefaultDispatcherProvider
import io.github.chronotask.engine.EngineConfig
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProducerTarget<out T>(val value: T) {
    var calls = 0
    @Once(id = "producer", enabled = false) fun run() { calls++ }
}

class ConsumerTarget<in T> {
    var calls = 0
    @Once(id = "consumer", enabled = false) fun run() { calls++ }
}

class SelfBoundTarget<T : Comparable<T>>(val value: T) {
    var calls = 0
    @Once(id = "self-bound", enabled = false) fun run() { calls++ }
}

class CrossBoundTarget<T, U : T>(val value: U) {
    var calls = 0
    @Once(id = "cross-bound", enabled = false) fun run() { calls++ }
}

class VariantGenericGenerationTest {
    private fun engine() = DefaultChronoTaskEngine(EngineConfig(
        dispatcherProvider = DefaultDispatcherProvider(Dispatchers.Unconfined)))

    @Test fun `covariant target generates invariant function parameters`() {
        val target = ProducerTarget("payload")
        engine().use { engine ->
            ProducerTarget_ChronoTaskBinding.register(engine, target).single().triggerNow()
            assertEquals(1, target.calls)
        }
    }

    @Test fun `contravariant target generates invariant function parameters`() {
        val target = ConsumerTarget<String>()
        engine().use { engine ->
            ConsumerTarget_ChronoTaskBinding.register(engine, target).single().triggerNow()
            assertEquals(1, target.calls)
        }
    }

    @Test fun `self referencing generic bounds resolve in generated functions`() {
        val target = SelfBoundTarget("payload")
        engine().use { engine ->
            SelfBoundTarget_ChronoTaskBinding.register(engine, target).single().triggerNow()
            assertEquals(1, target.calls)
        }
    }

    @Test fun `cross parameter bounds resolve in generated functions`() {
        val target = CrossBoundTarget<CharSequence, String>("payload")
        engine().use { engine ->
            CrossBoundTarget_ChronoTaskBinding.register(engine, target).single().triggerNow()
            assertEquals(1, target.calls)
        }
    }
}

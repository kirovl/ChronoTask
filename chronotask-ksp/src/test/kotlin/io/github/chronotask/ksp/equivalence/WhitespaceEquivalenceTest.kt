package io.github.chronotask.ksp.equivalence

import io.github.chronotask.annotation.*
import io.github.chronotask.engine.*
import io.github.chronotask.job.TaskExecutable
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.reflect.registerAnnotated
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WhitespaceTarget {
    @Once(id = " once ", name = " Once name ", dispatcher = " io ", enabled = false)
    fun once() {}
    @Scheduled(id = " scheduled ", name = " Scheduled name ", dispatcher = " io ", intervalMs = 1000, enabled = false)
    fun scheduled() {}
    @TickTask(id = " tick ", name = " Tick name ", dispatcher = " io ", intervalMs = 1000, enabled = false)
    fun tick() {}
    @Cron(id = " cron ", name = " Cron name ", dispatcher = " io ", expression = " 0 0 12 * * ? ", zone = " UTC ", enabled = false)
    fun cron() {}
}

class WhitespaceEquivalenceTest {
    @Test fun generatedAndReflectedMetadataMatchForEveryAnnotation() {
        val reflected = mutableListOf<TaskDefinition>()
        val engine = DefaultChronoTaskEngine(EngineConfig(
            dispatcherProvider = DefaultDispatcherProvider(Dispatchers.Unconfined, mapOf("io" to Dispatchers.Unconfined)),
            lifecycleListener = object : TaskLifecycleListener {
                override fun onRegistered(definition: TaskDefinition) { reflected += definition }
            }
        ))
        try {
            val target = WhitespaceTarget()
            engine.registerAnnotated(target)
            val generated = WhitespaceTarget_ChronoTaskBinding.definitions(target)
            val placeholder = TaskExecutable {}
            assertEquals(generated.map { it.copy(executable = placeholder) }.sortedBy { it.id },
                reflected.map { it.copy(executable = placeholder) }.sortedBy { it.id })
        } finally { engine.stop() }
    }
}

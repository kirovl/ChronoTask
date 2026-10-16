package io.github.chronotask.ksp.registration

import io.github.chronotask.annotation.Once
import io.github.chronotask.engine.*
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ReplacementRollbackTarget {
    @Once(id = "a", name = "batch-a", enabled = false)
    fun first() {}
    @Once(id = "b", enabled = false)
    fun second() {}
}

class KspReplacementRollbackTest {
    @Test fun rollbackMustPreserveReplacementRegistration() {
        lateinit var engine: DefaultChronoTaskEngine
        engine = DefaultChronoTaskEngine(EngineConfig(lifecycleListener = object : TaskLifecycleListener {
            override fun onRegistered(definition: TaskDefinition) {
                if (definition.id == "a" && definition.name == "batch-a") {
                    engine.unregister("a")
                    engine.register(TaskDefinition("a", name = "replacement", schedule = TaskSchedule.Once(0),
                        isEnabled = false, executable = {}))
                }
            }
        }))
        try {
            engine.register(TaskDefinition("b", schedule = TaskSchedule.Once(0), isEnabled = false, executable = {}))
            assertThrows<DuplicateTaskException> {
                ReplacementRollbackTarget_ChronoTaskBinding.register(engine, ReplacementRollbackTarget())
            }
            assertNotNull(engine.getHandle("a"))
        } finally { engine.stop() }
    }
}

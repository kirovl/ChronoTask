package io.github.chronotask.processor.engine

import io.github.chronotask.engine.*
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.processor.TestCompiler
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationTargetException

class AptReplacementRollbackTest {
    @Test fun rollbackMustPreserveReplacementRegistration() {
        TestCompiler.compileAndLoad("example.IsolationJob" to """
            package example;
            public class IsolationJob {
                @io.github.chronotask.annotation.Once(id = "a", name = "batch-a", enabled = false)
                public void first() {}
                @io.github.chronotask.annotation.Once(id = "b", enabled = false)
                public void second() {}
            }
        """.trimIndent()).use { env ->
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
                val target = env.loadClass("example.IsolationJob").getDeclaredConstructor().newInstance()
                val error = assertThrows(InvocationTargetException::class.java) {
                    env.register("example.IsolationJob_ChronoTaskBinding", engine, target)
                }
                assertInstanceOf(DuplicateTaskException::class.java, error.targetException)
                assertNotNull(engine.getHandle("a"))
            } finally { engine.stop() }
        }
    }
}

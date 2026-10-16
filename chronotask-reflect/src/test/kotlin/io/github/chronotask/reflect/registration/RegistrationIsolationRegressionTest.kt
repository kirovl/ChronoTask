package io.github.chronotask.reflect.registration

import io.github.chronotask.annotation.Once
import io.github.chronotask.annotation.Cron
import io.github.chronotask.engine.*
import io.github.chronotask.exception.TaskConfigurationException
import io.github.chronotask.job.TaskExecutable
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.reflect.AnnotatedTaskRegistrar
import io.github.chronotask.reflect.ReflectionTaskScanner
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RegistrationIsolationRegressionTest {
    class Batch {
        @Once(id = "a", name = "batch-a", enabled = false)
        fun first() {}
        @Once(id = "b", enabled = false)
        fun second() {}
    }

    @Test fun rollbackMustNotRemoveReplacementRegistration() {
        lateinit var engine: DefaultChronoTaskEngine
        engine = DefaultChronoTaskEngine(EngineConfig(lifecycleListener = object : TaskLifecycleListener {
            override fun onRegistered(definition: TaskDefinition) {
                if (definition.id == "a" && definition.name == "batch-a") {
                    engine.unregister("a")
                    engine.register(TaskDefinition("a", name = "replacement", schedule = TaskSchedule.Once(0),
                        isEnabled = false, executable = TaskExecutable {}))
                }
            }
        }))
        try {
            engine.register(TaskDefinition("b", schedule = TaskSchedule.Once(0), isEnabled = false, executable = TaskExecutable {}))
            assertThrows<DuplicateTaskException> { AnnotatedTaskRegistrar.register(engine, Batch()) }
            assertNotNull(engine.getHandle("a"), "Rollback must remove only registrations owned by the failed batch")
        } finally { engine.stop() }
    }

    class PaddedStrings {
        @Once(id = " task ", name = " Task name ", dispatcher = " io ")
        fun run() {}
    }

    @Test fun reflectionMetadataMustMatchCompileTimeWhitespaceNormalization() {
        val definition = ReflectionTaskScanner.scan(PaddedStrings()).single()
        assertAll(
            { assertEquals("task", definition.id) },
            { assertEquals("Task name", definition.name) },
            { assertEquals("io", definition.dispatcherKey) }
        )
    }

    class PaddedZone {
        @Cron(expression = "0 0 12 * * ?", zone = " UTC ")
        fun run() {}
    }

    @Test fun reflectionCronZoneMustMatchCompileTimeWhitespaceNormalization() {
        val definition = ReflectionTaskScanner.scan(PaddedZone()).single()
        assertEquals("UTC", (definition.schedule as TaskSchedule.Cron).zoneId.id)
    }

    class DuplicateAfterNormalization {
        @Once(id = "same") fun first() {}
        @Once(id = " same ") fun second() {}
    }

    @Test fun normalizedDuplicateIdsFailBeforeAnyRegistration() {
        val engine = DefaultChronoTaskEngine()
        try {
            assertThrows<TaskConfigurationException> {
                AnnotatedTaskRegistrar.register(engine, DuplicateAfterNormalization())
            }
            assertNull(engine.getHandle("same"))
            assertNull(engine.getHandle(" same "))
        } finally { engine.stop() }
    }
}

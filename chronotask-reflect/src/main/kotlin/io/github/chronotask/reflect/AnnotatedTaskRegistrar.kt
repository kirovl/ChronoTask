package io.github.chronotask.reflect

import io.github.chronotask.engine.ChronoTaskEngine
import io.github.chronotask.engine.TaskHandle
import io.github.chronotask.exception.ChronoTaskException

/**
 * Public entry point for scanning and registering annotated tasks into a [ChronoTaskEngine].
 */
object AnnotatedTaskRegistrar {

    /**
     * Scans [instance] for scheduling annotations and registers all discovered tasks into [engine].
     *
     * @param engine The engine into which tasks are registered.
     * @param instance The object instance containing annotated methods.
     * @return A list of [TaskHandle]s for all registered tasks.
     * @throws io.github.chronotask.exception.TaskConfigurationException if any method annotation or signature is invalid.
     * @throws io.github.chronotask.engine.DuplicateTaskException if a task ID conflicts with an existing task in the engine.
     * @throws io.github.chronotask.engine.EngineStateException if the engine is stopped.
     */
    @JvmStatic
    fun register(engine: ChronoTaskEngine, instance: Any): List<TaskHandle> {
        // Phase 1: Scan and validate all TaskDefinitions (Fail-Fast before mutating Engine)
        val definitions = ReflectionTaskScanner.scan(instance)
        if (definitions.isEmpty()) {
            return emptyList()
        }

        // Phase 2: Sequential registration with transactional best-effort rollback
        val registeredHandles = mutableListOf<TaskHandle>()
        try {
            for (definition in definitions) {
                val handle = engine.register(definition)
                registeredHandles.add(handle)
            }
            return registeredHandles
        } catch (registrationError: Throwable) {
            // Rollback all tasks registered in this specific batch
            var rollbackException: Throwable? = null
            for (handle in registeredHandles.asReversed()) {
                try {
                    handle.unregister()
                } catch (e: Throwable) {
                    if (rollbackException == null) {
                        rollbackException = e
                    } else {
                        rollbackException.addSuppressed(e)
                    }
                }
            }

            if (rollbackException != null) {
                registrationError.addSuppressed(rollbackException)
            }

            throw registrationError
        }
    }
}

/**
 * Convenience extension on [ChronoTaskEngine] to scan and register annotated methods from [instance].
 */
fun ChronoTaskEngine.registerAnnotated(instance: Any): List<TaskHandle> {
    return AnnotatedTaskRegistrar.register(this, instance)
}

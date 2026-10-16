package io.github.chronotask.engine

import io.github.chronotask.model.EngineState
import io.github.chronotask.model.TaskDefinition

/**
 * The core programmatic task scheduling engine for ChronoTask.
 */
interface ChronoTaskEngine : AutoCloseable {

    /**
     * Current lifecycle state of the engine.
     */
    val state: EngineState

    /**
     * Registers a new [TaskDefinition] into the engine.
     *
     * @param definition The task definition to register.
     * @return A [TaskHandle] bound to the registered task.
     * @throws DuplicateTaskException if a task with the same ID already exists.
     * @throws EngineStateException if the engine is [EngineState.STOPPED].
     */
    fun register(definition: TaskDefinition): TaskHandle

    /**
     * Unregisters a task by [taskId], cancelling its scheduler and any pending/running executions.
     *
     * @param taskId The task ID to unregister.
     * @return true if the task was found and unregistered, false otherwise.
     */
    fun unregister(taskId: String): Boolean

    /**
     * Returns the [TaskHandle] for the registered [taskId], or null if not found.
     */
    fun getHandle(taskId: String): TaskHandle?

    /**
     * Starts the engine, advancing from [EngineState.CREATED] to [EngineState.RUNNING] and launching schedulers.
     * Idempotent when already in [EngineState.RUNNING].
     * @throws EngineStateException if called after [EngineState.STOPPED].
     */
    fun start()

    /**
     * Pauses the engine, advancing from [EngineState.RUNNING] to [EngineState.PAUSED].
     * Pauses all automatic scheduled trigger generation without cancelling running executions.
     * Idempotent when already in [EngineState.PAUSED].
     */
    fun pause()

    /**
     * Resumes the engine from [EngineState.PAUSED] to [EngineState.RUNNING].
     * Resumes automatic scheduling for active tasks.
     * Idempotent when already in [EngineState.RUNNING].
     */
    fun resume()

    /**
     * Stops the engine permanently, cancelling all schedulers, running executions, and closing resources.
     * Terminal state; subsequent start/resume will throw [EngineStateException].
     * Idempotent when already in [EngineState.STOPPED].
     */
    fun stop()

    override fun close() = stop()

    companion object {
        @JvmStatic
        @JvmOverloads
        fun create(config: EngineConfig = EngineConfig()): ChronoTaskEngine = DefaultChronoTaskEngine(config)
    }
}

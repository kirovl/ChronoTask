package io.github.chronotask

import io.github.chronotask.builder.TaskBuilder
import io.github.chronotask.engine.ChronoTaskEngine
import io.github.chronotask.engine.EngineConfig

/**
 * Main entry point for ChronoTask engine creation and fluent task construction.
 */
object ChronoTask {

    /**
     * Creates a new [ChronoTaskEngine] with default production configuration.
     */
    @JvmStatic
    fun create(): ChronoTaskEngine = ChronoTaskEngine.create()

    /**
     * Creates a new [ChronoTaskEngine] with custom [config].
     */
    @JvmStatic
    fun create(config: EngineConfig): ChronoTaskEngine = ChronoTaskEngine.create(config)

    /**
     * Creates a fluent [TaskBuilder] to construct a [io.github.chronotask.model.TaskDefinition] for the specified [id].
     *
     * @param id Unique identifier of the task.
     */
    @JvmStatic
    fun newTask(id: String): TaskBuilder = TaskBuilder(id)
}

package io.github.chronotask.job

import io.github.chronotask.engine.ChronoTaskEngine
import io.github.chronotask.engine.TaskHandle
import io.github.chronotask.model.ConcurrencyPolicy
import io.github.chronotask.model.MisfirePolicy
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule

/**
 * Base abstract class for declarative, object-oriented scheduled tasks in ChronoTask.
 */
abstract class ChronoJob : JobDefinitionProvider {

    /**
     * Unique identifier for this task.
     */
    abstract val id: String

    /**
     * Human-readable display name. Defaults to [id].
     */
    open val name: String
        get() = id

    /**
     * Schedule defining when and how often this task triggers.
     */
    abstract val schedule: TaskSchedule

    /**
     * Policy governing concurrent execution. Defaults to [ConcurrencyPolicy.SkipIfRunning].
     */
    open val concurrencyPolicy: ConcurrencyPolicy
        get() = ConcurrencyPolicy.SkipIfRunning

    /**
     * Policy governing missed triggers. Defaults to [MisfirePolicy.Skip].
     */
    open val misfirePolicy: MisfirePolicy
        get() = MisfirePolicy.Skip

    /**
     * Optional custom execution dispatcher key. Defaults to null (engine default dispatcher).
     */
    open val dispatcherKey: String?
        get() = null

    /**
     * Whether this task is enabled upon registration. Defaults to true.
     */
    open val isEnabled: Boolean
        get() = true

    /**
     * Execution logic invoked upon each scheduled trigger.
     */
    abstract suspend fun execute(context: TaskExecutionContext)

    override fun toTaskDefinition(): TaskDefinition {
        return TaskDefinition(
            id = id,
            name = name,
            schedule = schedule,
            concurrencyPolicy = concurrencyPolicy,
            misfirePolicy = misfirePolicy,
            dispatcherKey = dispatcherKey,
            isEnabled = isEnabled,
            executable = TaskExecutable { ctx -> execute(ctx) }
        )
    }
}

/**
 * Registers a [ChronoJob] with the [ChronoTaskEngine].
 */
fun ChronoTaskEngine.register(job: ChronoJob): TaskHandle = register(job.toTaskDefinition())

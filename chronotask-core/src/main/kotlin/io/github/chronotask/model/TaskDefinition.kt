package io.github.chronotask.model

import io.github.chronotask.job.TaskExecutable

/**
 * Unified descriptor model for a scheduled task.
 *
 * @property id Unique identifier of the task. Must not be blank.
 * @property name Human-readable display name. Must not be blank. Defaults to [id].
 * @property schedule Schedule definition describing when the task triggers.
 * @property concurrencyPolicy Policy determining concurrent execution behavior.
 * @property misfirePolicy Policy determining handling of missed trigger times.
 * @property dispatcherKey Optional identifier specifying a custom execution dispatcher. Null indicates default engine dispatcher.
 * @property isEnabled Whether the task is active and eligible for scheduling.
 * @property executable The executable block or job to run upon each trigger.
 */
data class TaskDefinition @JvmOverloads constructor(
    val id: String,
    val name: String = id,
    val schedule: TaskSchedule,
    val concurrencyPolicy: ConcurrencyPolicy = ConcurrencyPolicy.SkipIfRunning,
    val misfirePolicy: MisfirePolicy = MisfirePolicy.Skip,
    val dispatcherKey: String? = null,
    val isEnabled: Boolean = true,
    val executable: TaskExecutable
) {
    init {
        require(id.isNotBlank()) { "Task id must not be blank" }
        require(name.isNotBlank()) { "Task name must not be blank" }
    }
}

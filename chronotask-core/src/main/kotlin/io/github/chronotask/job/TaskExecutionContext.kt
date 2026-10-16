package io.github.chronotask.job

import io.github.chronotask.trigger.ScheduledTrigger

/**
 * Execution context supplied to [TaskExecutable] upon each invocation.
 *
 * @property taskId The unique identifier of the executing task. Must not be blank.
 * @property trigger The scheduled trigger event that provoked this execution.
 * @property attempt The 1-based execution attempt counter. Must be > 0.
 */
data class TaskExecutionContext @JvmOverloads constructor(
    val taskId: String,
    val trigger: ScheduledTrigger,
    val attempt: Int = 1
) {
    init {
        require(taskId.isNotBlank()) { "taskId must not be blank" }
        require(attempt > 0) { "attempt must be positive: $attempt" }
    }
}

package io.github.chronotask.execution

import io.github.chronotask.trigger.ScheduledTrigger

/**
 * Represents a single planned or triggered execution instance of a task.
 *
 * @property executionId Unique identifier for this execution attempt.
 * @property taskId The identifier of the parent [io.github.chronotask.model.TaskDefinition].
 * @property trigger The [ScheduledTrigger] that produced this execution.
 * @property attempt The execution attempt number (1-based, >= 1).
 */
data class TaskExecution @JvmOverloads constructor(
    val executionId: ExecutionId = ExecutionId.generate(),
    val taskId: String,
    val trigger: ScheduledTrigger,
    val attempt: Int = 1
) {
    init {
        require(taskId.isNotBlank()) { "taskId must not be blank" }
        require(attempt > 0) { "attempt must be positive: $attempt" }
    }
}

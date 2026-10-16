package io.github.chronotask.concurrency

import io.github.chronotask.execution.ExecutionId
import io.github.chronotask.execution.TaskExecution
import io.github.chronotask.model.ConcurrencyPolicy
import io.github.chronotask.model.TaskDefinition
import kotlinx.coroutines.Job
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

/**
 * Internal state maintained per [TaskDefinition.id] for concurrency coordination.
 */
internal class TaskConcurrencyState(
    val taskId: String
) {
    val lock = Any()
    val activeJobs = ConcurrentHashMap<ExecutionId, Job>()
    var cancelled: Boolean = false
    var concurrencyPolicy: ConcurrencyPolicy? = null
    var currentRunningExecution: TaskExecution? = null

    // For QueueOne
    var pendingExecution: QueuedExecution? = null

    // For Serial
    val queue = ArrayDeque<QueuedExecution>()
}

/** Keeps queued work and its original definition together through dispatch and notifications. */
internal data class QueuedExecution(val definition: TaskDefinition, val execution: TaskExecution)

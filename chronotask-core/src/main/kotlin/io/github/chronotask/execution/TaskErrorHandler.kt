package io.github.chronotask.execution

import io.github.chronotask.model.TaskDefinition

/**
 * Handler invoked when a task execution throws an unhandled [Exception].
 */
fun interface TaskErrorHandler {

    /**
     * Handles an exception thrown during task execution.
     *
     * @param definition The task definition.
     * @param execution The execution attempt that failed.
     * @param error The unhandled exception thrown by the task executable.
     */
    fun onError(
        definition: TaskDefinition,
        execution: TaskExecution,
        error: Exception
    )
}

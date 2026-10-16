package io.github.chronotask.execution

import io.github.chronotask.model.TaskDefinition

/**
 * Executes a single [TaskExecution] with exception isolation and cancellation propagation.
 */
interface TaskRunner {

    /**
     * Executes the provided [execution] under the rules of [definition].
     *
     * When invoked by the concurrency controller, start and terminal events are
     * guaranteed even if the runner omits callbacks. Callbacks emitted by a runner
     * are deduplicated with these events; the first terminal notification wins.
     *
     * @param definition The task definition.
     * @param execution The execution attempt.
     * @param errorHandler Optional error handler for unhandled task exceptions.
     * @param listener Optional listener for lifecycle notifications.
     * @return [ExecutionResult] representing the terminal outcome.
     * @throws kotlinx.coroutines.CancellationException if cancelled.
     */
    suspend fun run(
        definition: TaskDefinition,
        execution: TaskExecution,
        errorHandler: TaskErrorHandler? = null,
        listener: ExecutionListener? = null
    ): ExecutionResult
}

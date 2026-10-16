package io.github.chronotask.execution

import io.github.chronotask.job.TaskExecutionContext
import io.github.chronotask.model.TaskDefinition
import kotlinx.coroutines.CancellationException

/**
 * Production implementation of [TaskRunner].
 *
 * Implements strict exception isolation:
 * - Catches [Exception] and delegates to [TaskErrorHandler].
 * - Never catches fatal JVM [Error]s.
 * - Always rethrows [CancellationException] after notifying observers.
 */
object DefaultTaskRunner : TaskRunner {

    override suspend fun run(
        definition: TaskDefinition,
        execution: TaskExecution,
        errorHandler: TaskErrorHandler?,
        listener: ExecutionListener?
    ): ExecutionResult {
        safeNotify { listener?.onStarted(definition, execution) }

        val context = TaskExecutionContext(
            taskId = execution.taskId,
            trigger = execution.trigger,
            attempt = execution.attempt
        )

        return try {
            definition.executable.execute(context)
            safeNotify { listener?.onSuccess(definition, execution) }
            ExecutionResult.Success
        } catch (e: CancellationException) {
            safeNotify { listener?.onCancelled(definition, execution) }
            throw e
        } catch (e: Exception) {
            safeNotify { errorHandler?.onError(definition, execution, e) }
            safeNotify { listener?.onFailure(definition, execution, e) }
            ExecutionResult.Failed(e)
        }
    }

    private inline fun safeNotify(action: () -> Unit) {
        try {
            action()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Observer errors must not corrupt the execution lifecycle or state
        }
    }
}

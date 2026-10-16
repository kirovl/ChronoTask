package io.github.chronotask.concurrency

import io.github.chronotask.execution.DefaultTaskRunner
import io.github.chronotask.execution.ExecutionId
import io.github.chronotask.execution.ExecutionListener
import io.github.chronotask.execution.ExecutionResult
import io.github.chronotask.execution.RejectionReason
import io.github.chronotask.execution.SkipReason
import io.github.chronotask.execution.SubmissionResult
import io.github.chronotask.execution.TaskErrorHandler
import io.github.chronotask.execution.TaskExecution
import io.github.chronotask.execution.TaskRunner
import io.github.chronotask.engine.DispatcherProvider
import io.github.chronotask.model.ConcurrencyPolicy
import io.github.chronotask.model.QueueOverflowPolicy
import io.github.chronotask.model.TaskDefinition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Production implementation of [ConcurrencyController].
 *
 * Implements fine-grained per-task locking, non-blocking TaskExecutable dispatching,
 * bounded queues, latest-wins single-slot pending queue, and structured failure isolation.
 */
class DefaultConcurrencyController @JvmOverloads constructor(
    private val coroutineScope: CoroutineScope? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val runner: TaskRunner = DefaultTaskRunner,
    private val errorHandler: TaskErrorHandler? = null,
    private val listener: ExecutionListener? = null
) : ConcurrencyController {

    private var executionDispatcherProvider: DispatcherProvider? = null

    internal constructor(
        coroutineScope: CoroutineScope,
        runner: TaskRunner,
        errorHandler: TaskErrorHandler?,
        listener: ExecutionListener?,
        dispatcherProvider: DispatcherProvider
    ) : this(coroutineScope = coroutineScope, runner = runner, errorHandler = errorHandler, listener = listener) {
        executionDispatcherProvider = dispatcherProvider
    }

    private val ownsScope = coroutineScope == null
    private val scope: CoroutineScope = coroutineScope ?: CoroutineScope(SupervisorJob() + dispatcher)
    private val isClosed = AtomicBoolean(false)
    private val taskStates = ConcurrentHashMap<String, TaskConcurrencyState>()
    private val registrationStates = ConcurrentHashMap.newKeySet<TaskConcurrencyState>()
    private val retiredStates = ConcurrentHashMap.newKeySet<TaskConcurrencyState>()

    override suspend fun submit(
        definition: TaskDefinition,
        execution: TaskExecution
    ): SubmissionResult {
        if (isClosed.get()) {
            return SubmissionResult.Rejected(execution, RejectionReason.CONTROLLER_CLOSED)
        }

        val state = taskStates.computeIfAbsent(definition.id) {
            TaskConcurrencyState(definition.id)
        }
        return submit(definition, execution, state)
    }

    // Engine registrations keep their state even after cancellation, so a late submission
    // cannot recreate a live state or share one with a replacement using the same task ID.
    internal suspend fun submit(
        definition: TaskDefinition,
        execution: TaskExecution,
        state: TaskConcurrencyState
    ): SubmissionResult {
        registrationStates.add(state)
        synchronized(state.lock) {
            if (isClosed.get() || state.cancelled) {
                registrationStates.remove(state)
                return SubmissionResult.Rejected(execution, RejectionReason.CONTROLLER_CLOSED)
            }
            // A task ID shares one coordination state. Mixing policies can strand queued
            // work or bypass the running slot, so pin the policy before accepting any work.
            require(state.concurrencyPolicy == null || state.concurrencyPolicy == definition.concurrencyPolicy) {
                "Cannot change concurrency policy for task '${definition.id}' from ${state.concurrencyPolicy} " +
                    "to ${definition.concurrencyPolicy}; cancelAll the task before submitting a different policy"
            }
            state.concurrencyPolicy = definition.concurrencyPolicy
        }

        return when (val policy = definition.concurrencyPolicy) {
            is ConcurrencyPolicy.Allow -> submitAllow(definition, execution, state)
            is ConcurrencyPolicy.SkipIfRunning -> submitSkipIfRunning(definition, execution, state)
            is ConcurrencyPolicy.QueueOne -> submitQueueOne(definition, execution, state)
            is ConcurrencyPolicy.Serial -> submitSerial(definition, execution, state, policy)
        }
    }

    private fun submitAllow(
        definition: TaskDefinition,
        execution: TaskExecution,
        state: TaskConcurrencyState
    ): SubmissionResult {
        return if (startExecution(definition, execution, state)) {
            SubmissionResult.Started(execution)
        } else {
            SubmissionResult.Rejected(execution, RejectionReason.CONTROLLER_CLOSED)
        }
    }

    private fun submitSkipIfRunning(
        definition: TaskDefinition,
        execution: TaskExecution,
        state: TaskConcurrencyState
    ): SubmissionResult {
        var shouldStart = false
        synchronized(state.lock) {
            if (isClosed.get() || state.cancelled) {
                return SubmissionResult.Rejected(execution, RejectionReason.CONTROLLER_CLOSED)
            }
            if (state.currentRunningExecution == null) {
                state.currentRunningExecution = execution
                shouldStart = true
            }
        }

        return if (shouldStart) {
            if (startExecution(definition, execution, state)) SubmissionResult.Started(execution)
            else SubmissionResult.Rejected(execution, RejectionReason.CONTROLLER_CLOSED)
        } else {
            safeNotify { listener?.onSkipped(definition, execution, SkipReason.ALREADY_RUNNING) }
            SubmissionResult.Skipped(execution, SkipReason.ALREADY_RUNNING)
        }
    }

    private fun submitQueueOne(
        definition: TaskDefinition,
        execution: TaskExecution,
        state: TaskConcurrencyState
    ): SubmissionResult {
        var shouldStart = false
        var replacedExecution: QueuedExecution? = null

        synchronized(state.lock) {
            if (isClosed.get() || state.cancelled) {
                return SubmissionResult.Rejected(execution, RejectionReason.CONTROLLER_CLOSED)
            }
            if (state.currentRunningExecution == null) {
                state.currentRunningExecution = execution
                shouldStart = true
            } else {
                replacedExecution = state.pendingExecution
                state.pendingExecution = QueuedExecution(definition, execution)
            }
        }

        return if (shouldStart) {
            if (startExecution(definition, execution, state)) SubmissionResult.Started(execution)
            else SubmissionResult.Rejected(execution, RejectionReason.CONTROLLER_CLOSED)
        } else {
            if (replacedExecution != null) {
                safeNotify { listener?.onSkipped(replacedExecution!!.definition, replacedExecution!!.execution, SkipReason.QUEUE_REPLACED) }
            }
            safeNotify { listener?.onQueued(definition, execution) }
            SubmissionResult.Queued(execution)
        }
    }

    private fun submitSerial(
        definition: TaskDefinition,
        execution: TaskExecution,
        state: TaskConcurrencyState,
        policy: ConcurrencyPolicy.Serial
    ): SubmissionResult {
        var shouldStart = false
        var droppedOldest: QueuedExecution? = null
        var isDroppedLatest = false
        var isRejected = false

        synchronized(state.lock) {
            if (isClosed.get() || state.cancelled) {
                return SubmissionResult.Rejected(execution, RejectionReason.CONTROLLER_CLOSED)
            }
            if (state.currentRunningExecution == null) {
                state.currentRunningExecution = execution
                shouldStart = true
            } else {
                if (state.queue.size < policy.maxQueueSize) {
                    state.queue.addLast(QueuedExecution(definition, execution))
                } else {
                    when (policy.overflowPolicy) {
                        QueueOverflowPolicy.DROP_OLDEST -> {
                            droppedOldest = state.queue.removeFirst()
                            state.queue.addLast(QueuedExecution(definition, execution))
                        }
                        QueueOverflowPolicy.DROP_LATEST -> {
                            isDroppedLatest = true
                        }
                        QueueOverflowPolicy.REJECT -> {
                            isRejected = true
                        }
                    }
                }
            }
        }

        if (shouldStart) {
            return if (startExecution(definition, execution, state)) SubmissionResult.Started(execution)
            else SubmissionResult.Rejected(execution, RejectionReason.CONTROLLER_CLOSED)
        }
        if (isRejected) {
            return SubmissionResult.Rejected(execution, RejectionReason.QUEUE_FULL)
        }
        if (isDroppedLatest) {
            safeNotify { listener?.onSkipped(definition, execution, SkipReason.QUEUE_FULL) }
            return SubmissionResult.Skipped(execution, SkipReason.QUEUE_FULL)
        }
        if (droppedOldest != null) {
            safeNotify { listener?.onSkipped(droppedOldest!!.definition, droppedOldest!!.execution, SkipReason.QUEUE_REPLACED) }
        }
        safeNotify { listener?.onQueued(definition, execution) }
        return SubmissionResult.Queued(execution)
    }

    private fun startExecution(
        definition: TaskDefinition,
        execution: TaskExecution,
        state: TaskConcurrencyState
    ): Boolean {
        return try {
            launchExecution(definition, execution, state)
        } catch (error: Throwable) {
            // Dispatcher lookup or coroutine construction can fail after reserving
            // the running slot. Release it and continue any already accepted queue.
            onExecutionFinished(definition, execution, state)
            throw error
        }
    }

    private fun launchExecution(
        definition: TaskDefinition,
        execution: TaskExecution,
        state: TaskConcurrencyState
    ): Boolean {
        val executionContext = executionDispatcherProvider?.get(definition.dispatcherKey) ?: EmptyCoroutineContext
        val started = AtomicBoolean(false)
        val terminalNotified = AtomicBoolean(false)
        val startNotified = AtomicBoolean(false)
        val executionListener = object : ExecutionListener by (listener ?: object : ExecutionListener {}) {
            override fun onStarted(definition: TaskDefinition, execution: TaskExecution) {
                if (startNotified.compareAndSet(false, true)) {
                    safeNotify { listener?.onStarted(definition, execution) }
                }
            }

            override fun onSuccess(definition: TaskDefinition, execution: TaskExecution) {
                if (terminalNotified.compareAndSet(false, true)) {
                    safeNotify { listener?.onSuccess(definition, execution) }
                }
            }

            override fun onFailure(definition: TaskDefinition, execution: TaskExecution, error: Throwable) {
                if (terminalNotified.compareAndSet(false, true)) {
                    safeNotify { listener?.onFailure(definition, execution, error) }
                }
            }

            override fun onSkipped(definition: TaskDefinition, execution: TaskExecution, reason: SkipReason) {
                if (terminalNotified.compareAndSet(false, true)) {
                    safeNotify { listener?.onSkipped(definition, execution, reason) }
                }
            }

            override fun onCancelled(definition: TaskDefinition, execution: TaskExecution) {
                if (terminalNotified.compareAndSet(false, true)) {
                    safeNotify { listener?.onCancelled(definition, execution) }
                }
            }
        }
        // Register the job before dispatch, so cancellation can always find it.
        val job = synchronized(state.lock) {
            if (isClosed.get() || state.cancelled) return false
            val pendingJob = scope.launch(executionContext, start = CoroutineStart.LAZY) {
                started.set(true)
                try {
                    // Runners can return an outcome without emitting listener callbacks.
                    // Deduplication preserves exactly one event for notifying runners too.
                    executionListener.onStarted(definition, execution)
                    when (val result = runner.run(definition, execution, errorHandler, executionListener)) {
                        is ExecutionResult.Success -> executionListener.onSuccess(definition, execution)
                        is ExecutionResult.Failed -> executionListener.onFailure(definition, execution, result.error)
                        is ExecutionResult.Cancelled -> executionListener.onCancelled(definition, execution)
                        is ExecutionResult.Skipped -> executionListener.onSkipped(definition, execution, result.reason)
                    }
                } catch (_: CancellationException) {
                    executionListener.onCancelled(definition, execution)
                } catch (error: Throwable) {
                    // Even Errors escaping the runner must release the engine's completion feedback.
                    executionListener.onFailure(definition, execution, error)
                }
            }
            state.activeJobs[execution.executionId] = pendingJob
            pendingJob
        }
        // A cancelled parent may complete a LAZY job before this registration. The
        // handler then runs synchronously, so attach it outside the state lock.
        job.invokeOnCompletion {
            try {
                if (!started.get()) {
                    executionListener.onCancelled(definition, execution)
                }
                // Completion handlers also run when cancellation prevents the body from starting.
                onExecutionFinished(definition, execution, state)
            } finally {
                state.activeJobs.remove(execution.executionId)
                if (state.activeJobs.isEmpty()) retiredStates.remove(state)
            }
        }
        job.start()
        return true
    }

    private fun onExecutionFinished(
        definition: TaskDefinition,
        finishedExecution: TaskExecution,
        state: TaskConcurrencyState
    ) {
        var completedExecution = finishedExecution
        while (true) {
            val nextExecution = synchronized(state.lock) {
                if (state.currentRunningExecution?.executionId == completedExecution.executionId) {
                    state.currentRunningExecution = null
                }
                if (isClosed.get() || state.cancelled) return
                val next = when (definition.concurrencyPolicy) {
                    is ConcurrencyPolicy.QueueOne -> state.pendingExecution.also { state.pendingExecution = null }
                    is ConcurrencyPolicy.Serial -> if (state.queue.isEmpty()) null else state.queue.removeFirst()
                    is ConcurrencyPolicy.Allow, is ConcurrencyPolicy.SkipIfRunning -> null
                }
                if (next != null) state.currentRunningExecution = next.execution
                next
            } ?: return

            try {
                if (!launchExecution(nextExecution.definition, nextExecution.execution, state)) {
                    safeNotify { listener?.onCancelled(nextExecution.definition, nextExecution.execution) }
                }
                return
            } catch (error: Throwable) {
                safeNotify { listener?.onFailure(nextExecution.definition, nextExecution.execution, error) }
                // Drain launch failures iteratively, without stranding the queue or
                // growing the stack when a dispatcher remains unavailable.
                completedExecution = nextExecution.execution
            }
        }
    }

    override fun cancelExecution(taskId: String, executionId: ExecutionId): Boolean {
        val state = taskStates[taskId] ?: return false
        var cancelled = false
        var cancelledExecution: QueuedExecution? = null
        var jobToCancel: Job? = null

        synchronized(state.lock) {
            val job = state.activeJobs[executionId]
            if (job != null && !job.isCompleted && !job.isCancelled) {
                jobToCancel = job
                cancelled = true
            }
            if (state.pendingExecution?.execution?.executionId == executionId) {
                cancelledExecution = state.pendingExecution
                state.pendingExecution = null
                cancelled = true
            }
            val iter = state.queue.iterator()
            while (iter.hasNext()) {
                val item = iter.next()
                if (item.execution.executionId == executionId) {
                    cancelledExecution = item
                    iter.remove()
                    cancelled = true
                    break
                }
            }
        }

        // Cancellation can synchronously run user listeners and completion handlers.
        jobToCancel?.cancel(CancellationException("Task execution cancelled"))
        cancelledExecution?.let { cancelledItem ->
            safeNotify { listener?.onCancelled(cancelledItem.definition, cancelledItem.execution) }
        }
        return cancelled
    }

    override fun cancelAll(taskId: String): Int {
        val state = taskStates[taskId] ?: return 0
        return cancelAll(state)
    }

    internal fun cancelAll(state: TaskConcurrencyState): Int {
        var cancelledCount = 0
        val cancelledList = ArrayList<QueuedExecution>()
        val jobs: List<Job>

        synchronized(state.lock) {
            state.cancelled = true
            retiredStates.add(state)
            taskStates.remove(state.taskId, state)
            registrationStates.remove(state)
            jobs = state.activeJobs.values.filter { !it.isCompleted }
            cancelledCount += jobs.size
            if (state.pendingExecution != null) {
                cancelledList.add(state.pendingExecution!!)
                state.pendingExecution = null
                cancelledCount++
            }
            while (state.queue.isNotEmpty()) {
                cancelledList.add(state.queue.removeFirst())
                cancelledCount++
            }
        }

        // A cancellation callback can run synchronously; the retired state must already be empty.
        for (job in jobs) {
            job.cancel(CancellationException("Task cancelled"))
        }
        for (item in cancelledList) {
            safeNotify { listener?.onCancelled(item.definition, item.execution) }
        }
        if (state.activeJobs.isEmpty()) retiredStates.remove(state)
        return cancelledCount
    }

    override fun activeCount(taskId: String): Int {
        val state = taskStates[taskId]
        return (state?.activeJobs?.size ?: 0) + retiredStates
            .filter { it.taskId == taskId && it !== state }
            .sumOf { it.activeJobs.size }
    }

    override fun queuedCount(taskId: String): Int {
        val state = taskStates[taskId] ?: return 0
        synchronized(state.lock) {
            return if (state.pendingExecution != null) 1 else state.queue.size
        }
    }

    override fun close() {
        if (isClosed.compareAndSet(false, true)) {
            for (state in (taskStates.values + registrationStates).toSet()) {
                cancelAll(state)
            }
            if (ownsScope) {
                scope.cancel()
            }
        }
    }

    private inline fun safeNotify(action: () -> Unit) {
        try {
            action()
        } catch (_: Throwable) {
            // Observer exceptions must not escape into concurrency controller
        }
    }
}

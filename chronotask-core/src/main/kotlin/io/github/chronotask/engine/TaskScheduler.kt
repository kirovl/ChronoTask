package io.github.chronotask.engine

import io.github.chronotask.clock.ChronoClock
import io.github.chronotask.clock.ChronoTimer
import io.github.chronotask.execution.TaskExecution
import io.github.chronotask.execution.ExecutionId
import io.github.chronotask.execution.SubmissionResult
import io.github.chronotask.model.EngineState
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.trigger.TriggerService
import io.github.chronotask.trigger.ScheduledTrigger
import io.github.chronotask.trigger.TriggerSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Internal scheduler orchestrating background scheduling loops for active [TaskRuntime] instances.
 */
internal class TaskScheduler(
    private val scope: CoroutineScope,
    private val clock: ChronoClock,
    private val timer: ChronoTimer,
    private val triggerService: TriggerService,
    private val submitExecution: suspend (TaskRuntime, TaskExecution) -> SubmissionResult,
    private val getEngineState: () -> EngineState
) {

    fun schedule(runtime: TaskRuntime) {
        val (previousJob, newJob) = synchronized(runtime.taskLock) {
            val engineState = getEngineState()
            if (engineState != EngineState.RUNNING || runtime.taskPaused || runtime.taskCancelled || runtime.scheduleCompleted) {
                return
            }

            val previousJob = runtime.currentSchedulerJob
            // Execution feedback survives scheduler restarts and pause/resume.
            val myGen = ++runtime.schedulerGeneration

            val job = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    runSchedulerLoop(runtime, myGen)
                } catch (_: CancellationException) {
                    // Pause, cancel, and collaborator cancellation remain resumable.
                } catch (error: Exception) {
                    synchronized(runtime.taskLock) {
                        if (runtime.schedulerGeneration == myGen) {
                            runtime.scheduleFailure = error
                            runtime.scheduleCompleted = true
                            runtime.nextScheduledAt = null
                        }
                    }
                }
            }
            runtime.currentSchedulerJob = job
            previousJob to job
        }
        // Cancellation and dispatch can invoke user callbacks synchronously. The new
        // generation must already be installed, and neither may run under taskLock.
        previousJob?.cancel(CancellationException("Restarting scheduler loop"))
        newJob.start()
    }

    fun stopSchedule(runtime: TaskRuntime, shouldStop: () -> Boolean = { true }) {
        val previousJob = synchronized(runtime.taskLock) {
            if (!shouldStop()) return
            val previousJob = runtime.currentSchedulerJob
            runtime.currentSchedulerJob = null
            runtime.schedulerGeneration++
            previousJob
        }
        previousJob?.cancel(CancellationException("Stopping task scheduler"))
    }

    fun notifyCompletion(runtime: TaskRuntime, executionId: ExecutionId) {
        val deferred = synchronized(runtime.taskLock) {
            if (runtime.completionExecutionId != executionId) return
            val d = runtime.completionDeferred
            runtime.completionDeferred = null
            runtime.completionExecutionId = null
            d
        }
        deferred?.complete(Unit)
    }

    private suspend fun runSchedulerLoop(runtime: TaskRuntime, myGen: Long) {
        while (scope.isActive) {
            var lastSched: Instant?
            var lastStart: Instant?
            var lastComp: Instant?
            var pendingCompletion: CompletableDeferred<Unit>?

            synchronized(runtime.taskLock) {
                if (runtime.schedulerGeneration != myGen ||
                    getEngineState() != EngineState.RUNNING ||
                    runtime.taskPaused ||
                    runtime.taskCancelled ||
                    runtime.scheduleCompleted
                ) {
                    return
                }
                lastSched = runtime.lastScheduledAt
                lastStart = runtime.lastStartedAt
                lastComp = runtime.lastCompletedAt
                pendingCompletion = runtime.completionDeferred
            }

            // Resuming a FixedDelay scheduler must still wait for its previous execution.
            if (pendingCompletion != null) {
                try {
                    pendingCompletion.await()
                } catch (_: CancellationException) {
                    return
                }
                continue
            }

            val now = clock.now()
            val timelineAnchor = synchronized(runtime.taskLock) {
                if (runtime.schedulerGeneration != myGen ||
                    getEngineState() != EngineState.RUNNING || runtime.taskPaused || runtime.taskCancelled
                ) return
                when (runtime.definition.schedule) {
                    is TaskSchedule.FixedRate, is TaskSchedule.FixedDelay, is TaskSchedule.Once -> {
                        runtime.scheduleActivatedAt ?: now.also { runtime.scheduleActivatedAt = it }
                    }
                    else -> runtime.registeredAt
                }
            }
            val result = triggerService.evaluate(
                schedule = runtime.definition.schedule,
                misfirePolicy = runtime.definition.misfirePolicy,
                registeredAt = timelineAnchor,
                lastScheduledAt = lastSched,
                lastStartedAt = lastStart,
                lastCompletedAt = lastComp,
                now = now
            )

            synchronized(runtime.taskLock) {
                if (runtime.schedulerGeneration != myGen) return
                runtime.nextScheduledAt = result.nextScheduledAt
                if (result.nextScheduledAt == null && result.triggers.isEmpty()) {
                    runtime.scheduleCompleted = true
                    return
                }
            }

            var completionToAwait: CompletableDeferred<Unit>? = null

            if (result.triggers.isEmpty()) {
                synchronized(runtime.taskLock) {
                    if (runtime.schedulerGeneration != myGen) return
                    if (result.alignedLastScheduledAt != null) {
                        runtime.lastScheduledAt = result.alignedLastScheduledAt
                    }
                }
            }
            // A resolver's next normal trigger is already planned. Commit to it
            // before sleeping, just as with a future non-misfire trigger. Re-evaluating
            // after waking turns ordinary timer lateness into another SKIP forever.
            val triggers = result.triggers.ifEmpty {
                result.nextScheduledAt?.let {
                    listOf(ScheduledTrigger(it, now, TriggerSource.SCHEDULE))
                } ?: emptyList()
            }
            for (trigger in triggers) {
                val currentNow = clock.now()
                if (trigger.scheduledAt.isAfter(currentNow)) {
                    timer.sleepUntil(trigger.scheduledAt, clock)
                }

                val execution = TaskExecution(
                    taskId = runtime.definition.id,
                    trigger = trigger
                )
                val deferred = if (runtime.definition.schedule is TaskSchedule.FixedDelay) {
                    CompletableDeferred<Unit>()
                } else null

                synchronized(runtime.taskLock) {
                    if (runtime.schedulerGeneration != myGen ||
                        getEngineState() != EngineState.RUNNING ||
                        runtime.taskPaused ||
                        runtime.taskCancelled
                    ) {
                        return
                    }
                    runtime.lastScheduledAt = trigger.scheduledAt
                    if (deferred != null) {
                        // Install feedback before submitting: execution may complete synchronously.
                        runtime.completionDeferred = deferred
                        runtime.completionExecutionId = execution.executionId
                    }
                }

                val submission = submitExecution(runtime, execution)
                if (deferred != null) {
                    if (submission is SubmissionResult.Started || submission is SubmissionResult.Queued) {
                        completionToAwait = deferred
                    } else {
                        // Skipped/rejected submissions will never deliver a completion callback.
                        synchronized(runtime.taskLock) {
                            if (runtime.completionExecutionId == execution.executionId) {
                                runtime.completionDeferred = null
                                runtime.completionExecutionId = null
                            }
                        }
                    }
                }
            }
            synchronized(runtime.taskLock) {
                if (runtime.schedulerGeneration != myGen) return
                // Advance past omitted catch-up triggers only after dispatching the bounded batch.
                // SKIP was aligned before its next normal trigger; do not overwrite that trigger.
                if (result.triggers.isNotEmpty() && result.alignedLastScheduledAt != null) {
                    runtime.lastScheduledAt = result.alignedLastScheduledAt
                }
            }

            if (completionToAwait != null) {
                try {
                    completionToAwait.await()
                } catch (_: CancellationException) {
                    return
                }
            }
        }
    }
}

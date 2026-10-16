package io.github.chronotask.engine

import io.github.chronotask.concurrency.DefaultConcurrencyController
import io.github.chronotask.execution.ExecutionListener
import io.github.chronotask.execution.ExecutionId
import io.github.chronotask.execution.RejectionReason
import io.github.chronotask.execution.SubmissionResult
import io.github.chronotask.execution.TaskExecution
import io.github.chronotask.model.EngineState
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.model.TaskState
import io.github.chronotask.trigger.ScheduledTrigger
import io.github.chronotask.trigger.TriggerService
import io.github.chronotask.trigger.TriggerSource
import io.github.chronotask.trigger.UnsupportedScheduleException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Production implementation of [ChronoTaskEngine].
 */
class DefaultChronoTaskEngine(
    val config: EngineConfig = EngineConfig()
) : ChronoTaskEngine {

    private val rootJob = SupervisorJob()
    private val engineScope = CoroutineScope(rootJob + config.dispatcherProvider.get(null))
    private val engineState = AtomicReference(EngineState.CREATED)
    private val lifecycleLock = Any()

    private val taskRegistry = ConcurrentHashMap<String, TaskRuntime>()
    private val executionOwners = ConcurrentHashMap<ExecutionId, TaskRuntime>()

    private val triggerService = TriggerService(
        clock = config.clock,
        misfireResolver = config.misfireResolver
    )

    private val internalExecutionListener: ExecutionListener = object : ExecutionListener {
        override fun onStarted(definition: TaskDefinition, execution: TaskExecution) {
            val runtime = executionOwners[execution.executionId]
            if (runtime != null) {
                synchronized(runtime.taskLock) {
                    if (runtime.startedExecutionIds.add(execution.executionId)) {
                        runtime.activeExecutions++
                        runtime.lastStartedAt = config.clock.now()
                    }
                }
            }
            safeNotify { config.executionListener?.onStarted(definition, execution) }
        }

        override fun onSuccess(definition: TaskDefinition, execution: TaskExecution) {
            handleExecutionCompletion(execution)
            safeNotify { config.executionListener?.onSuccess(definition, execution) }
        }

        override fun onFailure(definition: TaskDefinition, execution: TaskExecution, error: Throwable) {
            handleExecutionCompletion(execution)
            safeNotify { config.executionListener?.onFailure(definition, execution, error) }
        }

        override fun onCancelled(definition: TaskDefinition, execution: TaskExecution) {
            handleExecutionCompletion(execution)
            safeNotify { config.executionListener?.onCancelled(definition, execution) }
        }

        override fun onQueued(definition: TaskDefinition, execution: TaskExecution) {
            safeNotify { config.executionListener?.onQueued(definition, execution) }
        }

        override fun onSkipped(definition: TaskDefinition, execution: TaskExecution, reason: io.github.chronotask.execution.SkipReason) {
            handleExecutionCompletion(execution)
            safeNotify { config.executionListener?.onSkipped(definition, execution, reason) }
        }
    }

    private val concurrencyController = DefaultConcurrencyController(
        coroutineScope = engineScope,
        runner = config.runner,
        errorHandler = config.errorHandler,
        listener = internalExecutionListener,
        dispatcherProvider = config.dispatcherProvider
    )

    private val scheduler = TaskScheduler(
        scope = engineScope,
        clock = config.clock,
        timer = config.timer,
        triggerService = triggerService,
        submitExecution = ::submitExecution,
        getEngineState = { engineState.get() }
    )

    override val state: EngineState
        get() = engineState.get()

    override fun register(definition: TaskDefinition): TaskHandle {
        if (engineState.get() == EngineState.STOPPED) {
            throw EngineStateException("Cannot register tasks in STOPPED engine")
        }
        // Validate schedule early (e.g., cron expression parsing)
        io.github.chronotask.trigger.TriggerCalculatorFactory.create(definition.schedule)

        // Validate dispatcherKey early
        config.dispatcherProvider.get(definition.dispatcherKey)

        val now = config.clock.now()
        val runtime = TaskRuntime.create(definition, now)

        synchronized(lifecycleLock) {
            if (engineState.get() == EngineState.STOPPED) {
                throw EngineStateException("Cannot register tasks in STOPPED engine")
            }
            val existing = taskRegistry.putIfAbsent(definition.id, runtime)
            if (existing != null) {
                throw DuplicateTaskException("Task with id '${definition.id}' is already registered")
            }
        }

        safeNotify { config.lifecycleListener?.onRegistered(definition) }

        if (engineState.get() == EngineState.RUNNING && !runtime.taskPaused) {
            scheduler.schedule(runtime)
        }

        return TaskHandleImpl(runtime)
    }

    override fun unregister(taskId: String): Boolean {
        val runtime = taskRegistry.remove(taskId) ?: return false
        retireRegistration(runtime)
        return true
    }

    private fun retireRegistration(runtime: TaskRuntime) {
        synchronized(runtime.taskLock) {
            runtime.taskCancelled = true
        }
        scheduler.stopSchedule(runtime)
        concurrencyController.cancelAll(runtime.concurrencyState)

        safeNotify { config.lifecycleListener?.onUnregistered(runtime.definition) }
    }

    override fun getHandle(taskId: String): TaskHandle? {
        val runtime = taskRegistry[taskId] ?: return null
        return TaskHandleImpl(runtime)
    }

    override fun start() {
        while (true) {
            val current = engineState.get()
            when (current) {
                EngineState.CREATED -> {
                    if (engineState.compareAndSet(current, EngineState.RUNNING)) {
                        for ((_, runtime) in taskRegistry) {
                            if (!runtime.taskPaused && !runtime.taskCancelled) {
                                scheduler.schedule(runtime)
                            }
                        }
                        return
                    }
                }
                EngineState.RUNNING -> return
                EngineState.PAUSED -> {
                    resume()
                    return
                }
                EngineState.STOPPED -> throw EngineStateException("Cannot start STOPPED engine")
            }
        }
    }

    override fun pause() {
        while (true) {
            val current = engineState.get()
            when (current) {
                EngineState.RUNNING -> {
                    if (engineState.compareAndSet(current, EngineState.PAUSED)) {
                        for ((_, runtime) in taskRegistry) {
                            scheduler.stopSchedule(runtime) { engineState.get() == EngineState.PAUSED }
                        }
                        return
                    }
                }
                EngineState.PAUSED -> return
                EngineState.CREATED, EngineState.STOPPED -> throw EngineStateException("Cannot pause engine in state $current")
            }
        }
    }

    override fun resume() {
        while (true) {
            val current = engineState.get()
            when (current) {
                EngineState.PAUSED -> {
                    if (engineState.compareAndSet(current, EngineState.RUNNING)) {
                        for ((_, runtime) in taskRegistry) {
                            if (!runtime.taskPaused && !runtime.taskCancelled) {
                                scheduler.schedule(runtime)
                            }
                        }
                        return
                    }
                }
                EngineState.RUNNING -> return
                EngineState.CREATED, EngineState.STOPPED -> throw EngineStateException("Cannot resume engine in state $current")
            }
        }
    }

    override fun stop() {
        val runtimes = synchronized(lifecycleLock) {
            val previous = engineState.getAndSet(EngineState.STOPPED)
            if (previous == EngineState.STOPPED) return
            taskRegistry.values.toList().also { taskRegistry.clear() }
        }

        for (runtime in runtimes) {
            synchronized(runtime.taskLock) {
                runtime.taskCancelled = true
            }
            scheduler.stopSchedule(runtime)
        }
        concurrencyController.close()
        engineScope.cancel()
    }

    private fun handleExecutionCompletion(execution: TaskExecution) {
        val runtime = executionOwners.remove(execution.executionId)
        if (runtime != null) {
            val now = config.clock.now()
            synchronized(runtime.taskLock) {
                if (runtime.startedExecutionIds.remove(execution.executionId)) {
                    runtime.activeExecutions--
                    runtime.lastCompletedAt = now
                }
            }
            scheduler.notifyCompletion(runtime, execution.executionId)
        }
    }

    private suspend fun submitExecution(runtime: TaskRuntime, execution: TaskExecution): SubmissionResult {
        executionOwners[execution.executionId] = runtime
        return try {
            val result = concurrencyController.submit(runtime.definition, execution, runtime.concurrencyState)
            if (result is SubmissionResult.Rejected || result is SubmissionResult.Skipped) {
                executionOwners.remove(execution.executionId)
            }
            result
        } catch (error: Throwable) {
            executionOwners.remove(execution.executionId)
            throw error
        }
    }

    private inner class TaskHandleImpl(
        private val runtime: TaskRuntime
    ) : TaskHandle {

        override val taskId: String
            get() = runtime.definition.id

        override val state: TaskState
            get() = runtime.computeState(engineState.get())

        override fun pause() {
            synchronized(runtime.taskLock) {
                if (runtime.taskCancelled || !isRegistered()) return
                runtime.taskPaused = true
            }
            scheduler.stopSchedule(runtime) { runtime.taskPaused }
            safeNotify { config.lifecycleListener?.onPaused(runtime.definition) }
        }

        override fun resume() {
            synchronized(runtime.taskLock) {
                if (runtime.taskCancelled || !isRegistered()) return
                runtime.taskPaused = false
            }
            if (engineState.get() == EngineState.RUNNING) {
                scheduler.schedule(runtime)
            }
            safeNotify { config.lifecycleListener?.onResumed(runtime.definition) }
        }

        override fun cancel() {
            synchronized(runtime.taskLock) {
                if (runtime.taskCancelled) return
                runtime.taskCancelled = true
            }
            scheduler.stopSchedule(runtime)
            concurrencyController.cancelAll(runtime.concurrencyState)
            safeNotify { config.lifecycleListener?.onCancelled(runtime.definition) }
        }

        override fun unregister(): Boolean {
            if (!taskRegistry.remove(taskId, runtime)) return false
            retireRegistration(runtime)
            return true
        }

        override fun triggerNow(): SubmissionResult {
            if (runtime.taskCancelled || !isRegistered() || engineState.get() == EngineState.STOPPED) {
                val now = config.clock.now()
                val trigger = ScheduledTrigger(now, now, TriggerSource.MANUAL)
                return SubmissionResult.Rejected(
                    TaskExecution(taskId = taskId, trigger = trigger),
                    RejectionReason.CONTROLLER_CLOSED
                )
            }

            val now = config.clock.now()
            val trigger = ScheduledTrigger(
                scheduledAt = now,
                discoveredAt = now,
                source = TriggerSource.MANUAL
            )
            val execution = TaskExecution(
                taskId = taskId,
                trigger = trigger
            )

            return runBlocking {
                submitExecution(runtime, execution)
            }
        }

        override fun snapshot(): TaskRuntimeSnapshot {
            return runtime.snapshot(engineState.get())
        }

        private fun isRegistered(): Boolean {
            return taskRegistry[taskId]?.runtimeId == runtime.runtimeId
        }
    }

    private inline fun safeNotify(action: () -> Unit) {
        try {
            action()
        } catch (_: Throwable) {
            // Observer errors must never disrupt core engine state
        }
    }
}

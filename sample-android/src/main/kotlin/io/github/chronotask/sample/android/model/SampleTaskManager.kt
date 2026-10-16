package io.github.chronotask.sample.android.model

import io.github.chronotask.ChronoTask
import io.github.chronotask.dsl.schedule
import io.github.chronotask.engine.ChronoTaskEngine
import io.github.chronotask.engine.EngineConfig
import io.github.chronotask.engine.TaskHandle
import io.github.chronotask.execution.ExecutionListener
import io.github.chronotask.execution.SubmissionResult
import io.github.chronotask.execution.TaskExecution
import io.github.chronotask.model.EngineState
import io.github.chronotask.model.MisfirePolicy
import io.github.chronotask.model.TaskState
import io.github.chronotask.reflect.registerAnnotated
import io.github.chronotask.sample.android.tasks.KspSampleTasks
import io.github.chronotask.sample.android.tasks.KspSampleTasks_ChronoTaskBinding
import io.github.chronotask.sample.android.tasks.ReflectionSampleTasks
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class SampleTaskManager(private val engineConfig: EngineConfig = EngineConfig()) {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

    private val stateLock = Any()
    private var generation = 0L
    private var engine: ChronoTaskEngine? = null
    private val handles = ConcurrentHashMap<String, TaskHandle>()

    private val runCounters = ConcurrentHashMap<String, AtomicInteger>()
    private val successCounters = ConcurrentHashMap<String, AtomicInteger>()
    private val failureCounters = ConcurrentHashMap<String, AtomicInteger>()
    private val skippedCounters = ConcurrentHashMap<String, AtomicInteger>()

    private val lastStartedTimes = ConcurrentHashMap<String, String>()
    private val lastCompletedTimes = ConcurrentHashMap<String, String>()

    private val _engineState = MutableStateFlow(EngineState.CREATED)
    val engineState: StateFlow<EngineState> = _engineState.asStateFlow()

    private val _taskList = MutableStateFlow<List<SampleTaskUiState>>(emptyList())
    val taskList: StateFlow<List<SampleTaskUiState>> = _taskList.asStateFlow()

    private val _events = MutableStateFlow<List<SampleTaskEvent>>(emptyList())
    val events: StateFlow<List<SampleTaskEvent>> = _events.asStateFlow()

    init {
        initEngine()
    }

    fun addEvent(taskId: String, type: EventType, message: String) {
        val timestamp = timeFormatter.format(Instant.now())
        val thread = Thread.currentThread().name
        val event = SampleTaskEvent(timestamp, taskId, type, message, thread)

        _events.update { current ->
            current.takeLast(499) + event
        }
    }

    private fun handleTaskSuccess(taskId: String, count: Int, message: String) {
        runCounters.getOrPut(taskId) { AtomicInteger(0) }.set(count)
        successCounters.getOrPut(taskId) { AtomicInteger(0) }.incrementAndGet()
        val timeStr = timeFormatter.format(Instant.now())
        lastCompletedTimes[taskId] = timeStr

        addEvent(taskId, EventType.SUCCESS, message)
        updateTaskStateList()
    }

    private fun handleTaskFailure(taskId: String, message: String) {
        failureCounters.getOrPut(taskId) { AtomicInteger(0) }.incrementAndGet()
        addEvent(taskId, EventType.FAILED, message)
        updateTaskStateList()
    }

    private fun updateTaskStateList() = synchronized(stateLock) {
        val items = mutableListOf<SampleTaskUiState>()

        // 1. KSP tasks
        items.add(createTaskUiState("ksp-analytics-sync", "KSP", "FixedRate 3s (suspend, Context)"))
        items.add(createTaskUiState("ksp-cron-cleanup", "KSP", "Cron */10 * * * * ?"))
        items.add(createTaskUiState("ksp-onboarding-tip", "KSP", "Once 1s delay"))
        items.add(createTaskUiState("ksp-location-beacon", "KSP", "TickTask 2s beacon"))
        items.add(createTaskUiState("ksp-suspend-checkpoint", "KSP", "FixedRate 6s (suspend delay)"))

        // 2. Reflection task
        items.add(createTaskUiState("reflect-cache-evict", "Reflection", "FixedRate 5s"))

        // 3. Programmatic Kotlin DSL tasks
        items.add(createTaskUiState("dsl-slow-task", "Kotlin DSL", "FixedRate 1s (SkipIfRunning)", concurrency = "SKIP_IF_RUNNING"))
        items.add(createTaskUiState("dsl-queue-one-task", "Kotlin DSL", "FixedRate 4s (QueueOne)", concurrency = "QUEUE_ONE"))
        items.add(createTaskUiState("dsl-serial-demo", "Kotlin DSL", "FixedRate 3s (Serial Cap=2)", concurrency = "SERIAL(2)"))
        items.add(createTaskUiState("dsl-misfire-demo", "Kotlin DSL", "FixedRate 3s (FireOnce)"))
        items.add(createTaskUiState("dsl-catchup-demo", "Kotlin DSL", "FixedRate 2s (CatchUp 3)"))
        items.add(createTaskUiState("dsl-failure-demo", "Kotlin DSL", "FixedRate 4s (Failure Demo)"))

        _taskList.value = items
        _engineState.value = engine?.state ?: EngineState.STOPPED
    }

    private fun createTaskUiState(
        taskId: String,
        binding: String,
        scheduleDesc: String,
        concurrency: String = "SKIP_IF_RUNNING"
    ): SampleTaskUiState {
        val handle = handles[taskId]
        val state = handle?.state ?: TaskState.CANCELLED
        val runCount = runCounters[taskId]?.get() ?: 0
        val successCount = successCounters[taskId]?.get() ?: 0
        val failureCount = failureCounters[taskId]?.get() ?: 0
        val skippedCount = skippedCounters[taskId]?.get() ?: 0

        val started = lastStartedTimes[taskId]
        val completed = lastCompletedTimes[taskId]

        return SampleTaskUiState(
            id = taskId,
            bindingType = binding,
            scheduleDescription = scheduleDesc,
            state = state,
            runCount = runCount,
            successCount = successCount,
            failureCount = failureCount,
            skippedCount = skippedCount,
            lastStartedAt = started,
            lastCompletedAt = completed,
            concurrencyPolicy = concurrency
        )
    }

    fun initEngine() {
        val myGeneration = synchronized(stateLock) {
            generation++
            handles.clear()
            runCounters.clear()
            successCounters.clear()
            failureCounters.clear()
            skippedCounters.clear()
            lastStartedTimes.clear()
            lastCompletedTimes.clear()
            generation
        }
        engine?.stop()

        fun recordSuccess(taskId: String, count: Int, message: String) = synchronized(stateLock) {
            if (myGeneration == generation) handleTaskSuccess(taskId, count, message)
        }
        fun recordFailure(taskId: String, message: String) = synchronized(stateLock) {
            if (myGeneration == generation) handleTaskFailure(taskId, message)
        }

        addEvent("engine", EventType.START, "Creating ChronoTask 1.0.0 Engine...")

        val newEngine = ChronoTask.create(
            engineConfig.copy(
                executionListener = object : ExecutionListener {
                    override fun onStarted(definition: io.github.chronotask.model.TaskDefinition, execution: TaskExecution) {
                        synchronized(stateLock) {
                            if (myGeneration != generation) return
                            val taskId = definition.id
                            lastStartedTimes[taskId] = timeFormatter.format(Instant.now())
                            runCounters.getOrPut(taskId) { AtomicInteger(0) }.incrementAndGet()
                            addEvent(taskId, EventType.START, "Execution started (run #${runCounters[taskId]?.get()})")
                            updateTaskStateList()
                        }
                    }

                    override fun onSuccess(definition: io.github.chronotask.model.TaskDefinition, execution: TaskExecution) {
                        synchronized(stateLock) {
                            if (myGeneration != generation) return
                            // Refresh after the engine releases the running count.
                            updateTaskStateList()
                        }
                    }

                    override fun onFailure(definition: io.github.chronotask.model.TaskDefinition, execution: TaskExecution, error: Throwable) {
                        recordFailure(definition.id, error.message ?: "Execution failed")
                    }

                    override fun onSkipped(definition: io.github.chronotask.model.TaskDefinition, execution: TaskExecution, reason: io.github.chronotask.execution.SkipReason) {
                        synchronized(stateLock) {
                            if (myGeneration != generation) return
                            val taskId = definition.id
                            skippedCounters.getOrPut(taskId) { AtomicInteger(0) }.incrementAndGet()
                            addEvent(taskId, EventType.SKIPPED, "Skipped execution: $reason")
                            updateTaskStateList()
                        }
                    }

                    override fun onCancelled(definition: io.github.chronotask.model.TaskDefinition, execution: TaskExecution) {
                        synchronized(stateLock) {
                            if (myGeneration == generation) updateTaskStateList()
                        }
                    }
                }
            )
        )
        engine = newEngine

        // 1. KSP Registration
        val kspTasks = KspSampleTasks { id, count, msg ->
            recordSuccess(id, count, msg)
        }
        val kspHandles = KspSampleTasks_ChronoTaskBinding.register(newEngine, kspTasks)
        kspHandles.forEach { handles[it.taskId] = it }

        // 2. Reflection Registration
        val reflectTasks = ReflectionSampleTasks { id, count, msg ->
            recordSuccess(id, count, msg)
        }
        val reflectHandles = newEngine.registerAnnotated(reflectTasks)
        reflectHandles.forEach { handles[it.taskId] = it }

        // 3. Programmatic Kotlin DSL Tasks
        // Slow task (SkipIfRunning demo)
        val slowCounter = AtomicInteger(0)
        val slowHandle = newEngine.schedule("dsl-slow-task") {
            fixedRate(interval = 1.seconds, initialDelay = 1.seconds)
            skipIfRunning()
            execute {
                val c = slowCounter.incrementAndGet()
                delay(2200.milliseconds)
                recordSuccess("dsl-slow-task", c, "Slow task finished heavy computation #$c")
            }
        }
        handles[slowHandle.taskId] = slowHandle

        // QueueOne demo
        val queueOneCounter = AtomicInteger(0)
        val queueOneHandle = newEngine.schedule("dsl-queue-one-task") {
            fixedRate(interval = 4.seconds, initialDelay = 1.seconds)
            queueOne()
            execute {
                val c = queueOneCounter.incrementAndGet()
                recordSuccess("dsl-queue-one-task", c, "QueueOne batch execution #$c")
            }
        }
        handles[queueOneHandle.taskId] = queueOneHandle

        // Serial demo
        val serialCounter = AtomicInteger(0)
        val serialHandle = newEngine.schedule("dsl-serial-demo") {
            fixedRate(interval = 3.seconds, initialDelay = 1.seconds)
            serial(maxQueueSize = 2)
            execute {
                val c = serialCounter.incrementAndGet()
                delay(1500.milliseconds)
                recordSuccess("dsl-serial-demo", c, "Serial worker completed queue item #$c")
            }
        }
        handles[serialHandle.taskId] = serialHandle

        // Misfire FireOnce demo
        val misfireCounter = AtomicInteger(0)
        val misfireHandle = newEngine.schedule("dsl-misfire-demo") {
            fixedRate(interval = 3.seconds, initialDelay = 2.seconds)
            onMisfireFireOnce()
            execute {
                val c = misfireCounter.incrementAndGet()
                recordSuccess("dsl-misfire-demo", c, "Misfire FireOnce task run #$c")
            }
        }
        handles[misfireHandle.taskId] = misfireHandle

        // Misfire CatchUp demo
        val catchupCounter = AtomicInteger(0)
        val catchupHandle = newEngine.schedule("dsl-catchup-demo") {
            fixedRate(interval = 2.seconds, initialDelay = 2.seconds)
            onMisfireCatchUp(maxExecutions = 3)
            execute {
                val c = catchupCounter.incrementAndGet()
                recordSuccess("dsl-catchup-demo", c, "CatchUp recovery task run #$c")
            }
        }
        handles[catchupHandle.taskId] = catchupHandle

        // Failure demo task (Throws exception every 2nd attempt)
        val failureAttempts = AtomicInteger(0)
        val failureHandle = newEngine.schedule("dsl-failure-demo") {
            fixedRate(interval = 4.seconds, initialDelay = 2.seconds)
            execute {
                val a = failureAttempts.incrementAndGet()
                if (a % 2 == 0) {
                    throw IllegalStateException("Simulated transient exception in dsl-failure-demo (#$a)")
                } else {
                    recordSuccess("dsl-failure-demo", a, "Healthy run for failure demo (#$a)")
                }
            }
        }
        handles[failureHandle.taskId] = failureHandle

        addEvent("engine", EventType.SUCCESS, "Engine initialized with ${handles.size} multi-paradigm tasks")
        updateTaskStateList()
    }

    fun start() {
        engine?.start()
        addEvent("engine", EventType.START, "Engine started (RUNNING)")
        updateTaskStateList()
    }

    fun pause() {
        engine?.pause()
        addEvent("engine", EventType.PAUSED, "Engine paused")
        updateTaskStateList()
    }

    fun resume() {
        engine?.resume()
        addEvent("engine", EventType.RESUMED, "Engine resumed")
        updateTaskStateList()
    }

    fun stop() {
        engine?.stop()
        addEvent("engine", EventType.CANCELLED, "Engine stopped (Terminal State)")
        updateTaskStateList()
    }

    fun triggerTask(taskId: String) {
        val handle = handles[taskId]
        if (handle != null) {
            val result = handle.triggerNow()
            val msg = when (result) {
                is SubmissionResult.Started -> "Manual trigger immediately started"
                is SubmissionResult.Queued -> "Manual trigger queued"
                is SubmissionResult.Skipped -> "Manual trigger skipped: ${result.reason}"
                is SubmissionResult.Rejected -> "Manual trigger rejected: ${result.reason}"
            }
            addEvent(taskId, EventType.MANUAL, msg)
            updateTaskStateList()
        }
    }

    fun pauseTask(taskId: String) {
        handles[taskId]?.pause()
        addEvent(taskId, EventType.PAUSED, "Task paused manually")
        updateTaskStateList()
    }

    fun resumeTask(taskId: String) {
        handles[taskId]?.resume()
        addEvent(taskId, EventType.RESUMED, "Task resumed manually")
        updateTaskStateList()
    }

    fun cancelTask(taskId: String) {
        handles[taskId]?.cancel()
        addEvent(taskId, EventType.CANCELLED, "Task cancelled permanently")
        updateTaskStateList()
    }
}

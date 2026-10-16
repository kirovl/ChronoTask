package io.github.chronotask.dsl

import io.github.chronotask.cron.CronDialect
import io.github.chronotask.engine.ChronoTaskEngine
import io.github.chronotask.engine.TaskHandle
import io.github.chronotask.exception.TaskConfigurationException
import io.github.chronotask.job.TaskExecutable
import io.github.chronotask.job.TaskExecutionContext
import io.github.chronotask.model.ConcurrencyPolicy
import io.github.chronotask.model.MisfirePolicy
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.trigger.TriggerCalculator
import io.github.chronotask.trigger.TriggerContext
import java.time.Instant
import java.time.ZoneId
import kotlin.time.Duration

/**
 * Type-safe builder for configuring and creating a [TaskDefinition] in Kotlin DSL.
 *
 * @property id Unique identifier of the task.
 */
@ChronoTaskDsl
class TaskDsl(val id: String) {

    init {
        if (id.isBlank()) {
            throw TaskConfigurationException("Task id must not be blank")
        }
    }

    /**
     * Human-readable name of the task. Defaults to [id].
     */
    var name: String = id

    /**
     * Policy governing concurrent execution behavior. Defaults to [ConcurrencyPolicy.SkipIfRunning].
     */
    var concurrency: ConcurrencyPolicy = ConcurrencyPolicy.SkipIfRunning

    /**
     * Policy governing missed schedule trigger points. Defaults to [MisfirePolicy.Skip].
     */
    var misfire: MisfirePolicy = MisfirePolicy.Skip

    /**
     * Optional dispatcher key for custom thread pool execution. Defaults to null (engine default dispatcher).
     */
    var dispatcherKey: String? = null

    /**
     * Alias for [dispatcherKey].
     */
    var dispatcher: String?
        get() = dispatcherKey
        set(value) {
            dispatcherKey = value
        }

    /**
     * Whether the task is enabled upon start. Defaults to true.
     */
    var enabled: Boolean = true

    /**
     * Configures concurrency policy to [ConcurrencyPolicy.Allow].
     */
    fun allowConcurrent() {
        concurrency = ConcurrencyPolicy.Allow
    }

    /**
     * Configures concurrency policy to [ConcurrencyPolicy.SkipIfRunning].
     */
    fun skipIfRunning() {
        concurrency = ConcurrencyPolicy.SkipIfRunning
    }

    /**
     * Configures concurrency policy to [ConcurrencyPolicy.QueueOne].
     */
    fun queueOne() {
        concurrency = ConcurrencyPolicy.QueueOne
    }

    /**
     * Configures concurrency policy to [ConcurrencyPolicy.Serial] with specified queue capacity.
     */
    fun serial(
        maxQueueSize: Int,
        overflowPolicy: io.github.chronotask.model.QueueOverflowPolicy = io.github.chronotask.model.QueueOverflowPolicy.DROP_OLDEST
    ) {
        if (maxQueueSize <= 0) {
            throw TaskConfigurationException("maxQueueSize must be positive: $maxQueueSize")
        }
        concurrency = ConcurrencyPolicy.Serial(maxQueueSize, overflowPolicy)
    }

    /**
     * Configures misfire policy to [MisfirePolicy.Skip].
     */
    fun onMisfireSkip() {
        misfire = MisfirePolicy.Skip
    }

    /**
     * Configures misfire policy to [MisfirePolicy.FireOnce].
     */
    fun onMisfireFireOnce() {
        misfire = MisfirePolicy.FireOnce
    }

    /**
     * Configures misfire policy to [MisfirePolicy.CatchUp].
     */
    fun onMisfireCatchUp(maxExecutions: Int) {
        if (maxExecutions <= 0) {
            throw TaskConfigurationException("maxExecutions must be positive: $maxExecutions")
        }
        misfire = MisfirePolicy.CatchUp(maxExecutions)
    }

    private var configuredSchedule: TaskSchedule? = null
    private var configuredExecutable: TaskExecutable? = null

    /**
     * Configures a fixed-rate schedule.
     *
     * @param interval Interval between triggers.
     * @param initialDelay Initial delay before first trigger. Defaults to [Duration.ZERO].
     */
    fun fixedRate(interval: Duration, initialDelay: Duration = Duration.ZERO) {
        setSchedule(
            TaskSchedule.FixedRate(
                intervalMs = interval.toScheduleIntervalMs("interval"),
                initialDelayMs = initialDelay.toScheduleDelayMs("initialDelay")
            )
        )
    }

    /**
     * Configures a fixed-delay schedule.
     *
     * @param delay Delay between execution completion and next trigger.
     * @param initialDelay Initial delay before first trigger. Defaults to [Duration.ZERO].
     */
    fun fixedDelay(delay: Duration, initialDelay: Duration = Duration.ZERO) {
        setSchedule(
            TaskSchedule.FixedDelay(
                delayMs = delay.toScheduleIntervalMs("delay"),
                initialDelayMs = initialDelay.toScheduleDelayMs("initialDelay")
            )
        )
    }

    /**
     * Configures a one-time execution schedule.
     *
     * @param delay Delay before firing. Defaults to [Duration.ZERO].
     */
    fun once(delay: Duration = Duration.ZERO) {
        setSchedule(
            TaskSchedule.Once(
                delayMs = delay.toScheduleDelayMs("delay")
            )
        )
    }

    /**
     * Configures a Cron expression schedule.
     *
     * @param expression Cron expression string.
     * @param zone Target time zone.
     * @param dialect Syntax dialect (defaults to [CronDialect.QUARTZ]).
     */
    fun cron(
        expression: String,
        zone: ZoneId,
        dialect: CronDialect = CronDialect.QUARTZ
    ) {
        setSchedule(
            TaskSchedule.Cron(
                expression = expression,
                zoneId = zone,
                dialect = dialect
            )
        )
    }

    /**
     * Configures a custom programmatic schedule evaluated by a [TriggerCalculator].
     */
    fun custom(calculator: TriggerCalculator) {
        setSchedule(TaskSchedule.Custom(calculator))
    }

    /**
     * Configures a custom programmatic schedule evaluated by a lambda.
     */
    fun custom(calculator: (TriggerContext) -> Instant?) {
        setSchedule(TaskSchedule.Custom(calculator))
    }

    private fun setSchedule(schedule: TaskSchedule) {
        if (configuredSchedule != null) {
            throw TaskConfigurationException(
                "Task '$id' already has a schedule configured ($configuredSchedule). Only exactly one schedule is allowed per task."
            )
        }
        configuredSchedule = schedule
    }

    /**
     * Configures the coroutine suspending executable block for each trigger.
     */
    fun execute(block: suspend (TaskExecutionContext) -> Unit) {
        if (configuredExecutable != null) {
            throw TaskConfigurationException("Task '$id' already has an executable configured.")
        }
        configuredExecutable = TaskExecutable(block)
    }

    /**
     * Configures a [TaskExecutable] for each trigger.
     */
    fun execute(executable: TaskExecutable) {
        if (configuredExecutable != null) {
            throw TaskConfigurationException("Task '$id' already has an executable configured.")
        }
        configuredExecutable = executable
    }

    /**
     * Builds and validates the immutable [TaskDefinition].
     *
     * @throws TaskConfigurationException if required fields (schedule, executable) are missing or invalid.
     */
    fun build(): TaskDefinition {
        if (name.isBlank()) {
            throw TaskConfigurationException("Task name must not be blank")
        }
        val schedule = configuredSchedule
            ?: throw TaskConfigurationException("Schedule is required for task '$id'")
        val executable = configuredExecutable
            ?: throw TaskConfigurationException("Executable is required for task '$id'")

        return TaskDefinition(
            id = id,
            name = name,
            schedule = schedule,
            concurrencyPolicy = concurrency,
            misfirePolicy = misfire,
            dispatcherKey = dispatcherKey,
            isEnabled = enabled,
            executable = executable
        )
    }
}

/**
 * Convenience extension to configure and register a task with [ChronoTaskEngine] using Kotlin DSL.
 *
 * @param id Unique identifier of the task.
 * @param block DSL configuration lambda.
 * @return [TaskHandle] representing the registered task.
 */
fun ChronoTaskEngine.schedule(id: String, block: TaskDsl.() -> Unit): TaskHandle {
    val dsl = TaskDsl(id).apply(block)
    val definition = dsl.build()
    return register(definition)
}

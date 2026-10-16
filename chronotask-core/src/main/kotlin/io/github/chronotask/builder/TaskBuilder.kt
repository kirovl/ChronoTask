package io.github.chronotask.builder

import io.github.chronotask.cron.CronDialect
import io.github.chronotask.dsl.toScheduleDelayMs
import io.github.chronotask.dsl.toScheduleIntervalMs
import io.github.chronotask.exception.TaskConfigurationException
import io.github.chronotask.job.JavaTaskExecutable
import io.github.chronotask.job.TaskExecutable
import io.github.chronotask.model.ConcurrencyPolicy
import io.github.chronotask.model.MisfirePolicy
import io.github.chronotask.model.QueueOverflowPolicy
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.trigger.TriggerCalculator
import java.time.Duration
import java.time.ZoneId

/**
 * Fluent builder for creating [TaskDefinition] instances from Java and Kotlin.
 */
class TaskBuilder(private val id: String) {

    init {
        if (id.isBlank()) {
            throw TaskConfigurationException("Task id must not be blank")
        }
    }

    private var taskName: String = id
    private var taskSchedule: TaskSchedule? = null
    private var concurrencyPolicy: ConcurrencyPolicy = ConcurrencyPolicy.SkipIfRunning
    private var misfirePolicy: MisfirePolicy = MisfirePolicy.Skip
    private var dispatcherKey: String? = null
    private var isEnabled: Boolean = true
    private var taskExecutable: TaskExecutable? = null

    /**
     * Sets the human-readable display name of the task.
     */
    fun name(name: String): TaskBuilder = apply {
        if (name.isBlank()) {
            throw TaskConfigurationException("Task name must not be blank")
        }
        this.taskName = name
    }

    /**
     * Sets a fixed-rate schedule with zero initial delay.
     */
    fun fixedRate(interval: Duration): TaskBuilder = fixedRate(interval, Duration.ZERO)

    /**
     * Sets a fixed-rate schedule with specified interval and initial delay.
     */
    fun fixedRate(interval: Duration, initialDelay: Duration): TaskBuilder = apply {
        setSchedule(
            TaskSchedule.FixedRate(
                intervalMs = interval.toScheduleIntervalMs("interval"),
                initialDelayMs = initialDelay.toScheduleDelayMs("initialDelay")
            )
        )
    }

    /**
     * Sets a fixed-delay schedule with zero initial delay.
     */
    fun fixedDelay(delay: Duration): TaskBuilder = fixedDelay(delay, Duration.ZERO)

    /**
     * Sets a fixed-delay schedule with specified delay and initial delay.
     */
    fun fixedDelay(delay: Duration, initialDelay: Duration): TaskBuilder = apply {
        setSchedule(
            TaskSchedule.FixedDelay(
                delayMs = delay.toScheduleIntervalMs("delay"),
                initialDelayMs = initialDelay.toScheduleDelayMs("initialDelay")
            )
        )
    }

    /**
     * Sets a one-time schedule with specified delay.
     */
    fun once(delay: Duration): TaskBuilder = apply {
        setSchedule(
            TaskSchedule.Once(
                delayMs = delay.toScheduleDelayMs("delay")
            )
        )
    }

    /**
     * Sets a Cron schedule with [CronDialect.QUARTZ] dialect.
     */
    fun cron(expression: String, zone: ZoneId): TaskBuilder = cron(expression, zone, CronDialect.QUARTZ)

    /**
     * Sets a Cron schedule with specified dialect.
     */
    fun cron(expression: String, zone: ZoneId, dialect: CronDialect): TaskBuilder = apply {
        setSchedule(
            TaskSchedule.Cron(
                expression = expression,
                zoneId = zone,
                dialect = dialect
            )
        )
    }

    /**
     * Sets a custom programmatic schedule evaluated by a [TriggerCalculator].
     */
    fun custom(calculator: TriggerCalculator): TaskBuilder = apply {
        setSchedule(TaskSchedule.Custom(calculator))
    }

    private fun setSchedule(schedule: TaskSchedule) {
        if (this.taskSchedule != null) {
            throw TaskConfigurationException(
                "Task '$id' already has a schedule configured ($taskSchedule). Only exactly one schedule is allowed per task."
            )
        }
        this.taskSchedule = schedule
    }

    /**
     * Sets concurrency policy to [ConcurrencyPolicy.Allow].
     */
    fun allowConcurrent(): TaskBuilder = apply {
        this.concurrencyPolicy = ConcurrencyPolicy.Allow
    }

    /**
     * Sets concurrency policy to [ConcurrencyPolicy.SkipIfRunning].
     */
    fun skipIfRunning(): TaskBuilder = apply {
        this.concurrencyPolicy = ConcurrencyPolicy.SkipIfRunning
    }

    /**
     * Sets concurrency policy to [ConcurrencyPolicy.QueueOne].
     */
    fun queueOne(): TaskBuilder = apply {
        this.concurrencyPolicy = ConcurrencyPolicy.QueueOne
    }

    /**
     * Sets concurrency policy to [ConcurrencyPolicy.Serial] with [QueueOverflowPolicy.DROP_OLDEST].
     */
    fun serial(maxQueueSize: Int): TaskBuilder = serial(maxQueueSize, QueueOverflowPolicy.DROP_OLDEST)

    /**
     * Sets concurrency policy to [ConcurrencyPolicy.Serial] with specified queue capacity and overflow policy.
     */
    fun serial(maxQueueSize: Int, overflowPolicy: QueueOverflowPolicy): TaskBuilder = apply {
        if (maxQueueSize <= 0) {
            throw TaskConfigurationException("maxQueueSize must be positive: $maxQueueSize")
        }
        this.concurrencyPolicy = ConcurrencyPolicy.Serial(maxQueueSize, overflowPolicy)
    }

    /**
     * Sets concurrency policy directly.
     */
    fun concurrencyPolicy(policy: ConcurrencyPolicy): TaskBuilder = apply {
        this.concurrencyPolicy = policy
    }

    /**
     * Sets misfire policy to [MisfirePolicy.Skip].
     */
    fun onMisfireSkip(): TaskBuilder = apply {
        this.misfirePolicy = MisfirePolicy.Skip
    }

    /**
     * Sets misfire policy to [MisfirePolicy.FireOnce].
     */
    fun onMisfireFireOnce(): TaskBuilder = apply {
        this.misfirePolicy = MisfirePolicy.FireOnce
    }

    /**
     * Sets misfire policy to [MisfirePolicy.CatchUp].
     */
    fun onMisfireCatchUp(maxExecutions: Int): TaskBuilder = apply {
        if (maxExecutions <= 0) {
            throw TaskConfigurationException("maxExecutions must be positive: $maxExecutions")
        }
        this.misfirePolicy = MisfirePolicy.CatchUp(maxExecutions)
    }

    /**
     * Sets misfire policy directly.
     */
    fun misfirePolicy(policy: MisfirePolicy): TaskBuilder = apply {
        this.misfirePolicy = policy
    }

    /**
     * Sets custom execution dispatcher key.
     */
    fun dispatcher(dispatcherKey: String?): TaskBuilder = apply {
        this.dispatcherKey = dispatcherKey
    }

    /**
     * Sets whether the task is enabled upon registration.
     */
    fun enabled(enabled: Boolean): TaskBuilder = apply {
        this.isEnabled = enabled
    }

    /**
     * Sets execution logic using a [JavaTaskExecutable] functional interface.
     */
    fun execute(executable: JavaTaskExecutable): TaskBuilder = apply {
        if (this.taskExecutable != null) {
            throw TaskConfigurationException("Task '$id' already has an executable configured.")
        }
        this.taskExecutable = TaskExecutable.fromJava(executable)
    }

    /**
     * Sets execution logic using a standard [Runnable].
     */
    fun executeRunnable(runnable: Runnable): TaskBuilder = apply {
        if (this.taskExecutable != null) {
            throw TaskConfigurationException("Task '$id' already has an executable configured.")
        }
        this.taskExecutable = TaskExecutable.fromJava { _ -> runnable.run() }
    }

    /**
     * Sets execution logic using a Kotlin [TaskExecutable].
     */
    fun execute(executable: TaskExecutable): TaskBuilder = apply {
        if (this.taskExecutable != null) {
            throw TaskConfigurationException("Task '$id' already has an executable configured.")
        }
        this.taskExecutable = executable
    }

    /**
     * Builds and validates the immutable [TaskDefinition].
     *
     * @throws TaskConfigurationException if required fields are missing or invalid.
     */
    fun build(): TaskDefinition {
        if (taskName.isBlank()) {
            throw TaskConfigurationException("Task name must not be blank")
        }
        val schedule = taskSchedule
            ?: throw TaskConfigurationException("Schedule is required for task '$id'")
        val executable = taskExecutable
            ?: throw TaskConfigurationException("Executable is required for task '$id'")

        return TaskDefinition(
            id = id,
            name = taskName,
            schedule = schedule,
            concurrencyPolicy = concurrencyPolicy,
            misfirePolicy = misfirePolicy,
            dispatcherKey = dispatcherKey,
            isEnabled = isEnabled,
            executable = executable
        )
    }
}

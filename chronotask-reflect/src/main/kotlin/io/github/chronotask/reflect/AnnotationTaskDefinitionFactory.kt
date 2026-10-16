package io.github.chronotask.reflect

import io.github.chronotask.annotation.AnnotationConcurrency
import io.github.chronotask.annotation.AnnotationMisfire
import io.github.chronotask.annotation.Cron
import io.github.chronotask.annotation.Once
import io.github.chronotask.annotation.ScheduleMode
import io.github.chronotask.annotation.Scheduled
import io.github.chronotask.annotation.TickTask
import io.github.chronotask.cron.CronDialect
import io.github.chronotask.cron.CronParseException
import io.github.chronotask.cron.CronParser
import io.github.chronotask.exception.TaskConfigurationException
import io.github.chronotask.job.TaskExecutable
import io.github.chronotask.model.ConcurrencyPolicy
import io.github.chronotask.model.MisfirePolicy
import io.github.chronotask.model.QueueOverflowPolicy
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import java.time.DateTimeException
import java.time.ZoneId

/**
 * Internal factory creating immutable [TaskDefinition] instances from scheduling annotations.
 */
internal object AnnotationTaskDefinitionFactory {

    fun createFromScheduled(
        annotation: Scheduled,
        resolvedId: String,
        executable: TaskExecutable
    ): TaskDefinition {
        if (annotation.intervalMs <= 0) {
            throw TaskConfigurationException("Task '$resolvedId' intervalMs must be positive: ${annotation.intervalMs}")
        }
        if (annotation.initialDelayMs < 0) {
            throw TaskConfigurationException("Task '$resolvedId' initialDelayMs must be non-negative: ${annotation.initialDelayMs}")
        }

        val schedule = when (annotation.mode) {
            ScheduleMode.FIXED_RATE -> TaskSchedule.FixedRate(
                intervalMs = annotation.intervalMs,
                initialDelayMs = annotation.initialDelayMs
            )
            ScheduleMode.FIXED_DELAY -> TaskSchedule.FixedDelay(
                delayMs = annotation.intervalMs,
                initialDelayMs = annotation.initialDelayMs
            )
        }

        return buildDefinition(
            id = annotation.id.trim().ifBlank { resolvedId },
            name = annotation.name.trim().ifBlank { annotation.id.trim().ifBlank { resolvedId } },
            schedule = schedule,
            concurrency = annotation.concurrency,
            serialQueueSize = annotation.serialQueueSize,
            serialOverflow = annotation.serialOverflow,
            misfire = annotation.misfire,
            catchUpLimit = annotation.catchUpLimit,
            dispatcher = annotation.dispatcher,
            enabled = annotation.enabled,
            executable = executable
        )
    }

    fun createFromCron(
        annotation: Cron,
        resolvedId: String,
        executable: TaskExecutable
    ): TaskDefinition {
        if (annotation.expression.isBlank()) {
            throw TaskConfigurationException("Task '$resolvedId' cron expression must not be blank")
        }

        val zoneId = try {
            ZoneId.of(annotation.zone.trim())
        } catch (e: DateTimeException) {
            throw TaskConfigurationException("Task '$resolvedId' invalid cron zone: '${annotation.zone}'", e)
        } catch (e: Exception) {
            throw TaskConfigurationException("Task '$resolvedId' invalid cron zone: '${annotation.zone}'", e)
        }

        val expression = annotation.expression.trim()
        try {
            // Validate the entire batch during scanning, before any task can register or run.
            CronParser.parse(expression, annotation.dialect)
        } catch (e: CronParseException) {
            throw TaskConfigurationException(
                "Task '$resolvedId' invalid cron expression '$expression' (${annotation.dialect}): ${e.reason}",
                e
            )
        }

        val schedule = TaskSchedule.Cron(
            expression = expression,
            zoneId = zoneId,
            dialect = annotation.dialect
        )

        return buildDefinition(
            id = annotation.id.trim().ifBlank { resolvedId },
            name = annotation.name.trim().ifBlank { annotation.id.trim().ifBlank { resolvedId } },
            schedule = schedule,
            concurrency = annotation.concurrency,
            serialQueueSize = annotation.serialQueueSize,
            serialOverflow = annotation.serialOverflow,
            misfire = annotation.misfire,
            catchUpLimit = annotation.catchUpLimit,
            dispatcher = annotation.dispatcher,
            enabled = annotation.enabled,
            executable = executable
        )
    }

    fun createFromOnce(
        annotation: Once,
        resolvedId: String,
        executable: TaskExecutable
    ): TaskDefinition {
        if (annotation.delayMs < 0) {
            throw TaskConfigurationException("Task '$resolvedId' delayMs must be non-negative: ${annotation.delayMs}")
        }

        val schedule = TaskSchedule.Once(delayMs = annotation.delayMs)

        return buildDefinition(
            id = annotation.id.trim().ifBlank { resolvedId },
            name = annotation.name.trim().ifBlank { annotation.id.trim().ifBlank { resolvedId } },
            schedule = schedule,
            concurrency = annotation.concurrency,
            serialQueueSize = annotation.serialQueueSize,
            serialOverflow = annotation.serialOverflow,
            misfire = annotation.misfire,
            catchUpLimit = annotation.catchUpLimit,
            dispatcher = annotation.dispatcher,
            enabled = annotation.enabled,
            executable = executable
        )
    }

    fun createFromTickTask(
        annotation: TickTask,
        resolvedId: String,
        executable: TaskExecutable
    ): TaskDefinition {
        if (annotation.intervalMs <= 0) {
            throw TaskConfigurationException("Task '$resolvedId' intervalMs must be positive: ${annotation.intervalMs}")
        }
        if (annotation.initialDelayMs < 0) {
            throw TaskConfigurationException("Task '$resolvedId' initialDelayMs must be non-negative: ${annotation.initialDelayMs}")
        }

        val schedule = TaskSchedule.FixedRate(
            intervalMs = annotation.intervalMs,
            initialDelayMs = annotation.initialDelayMs
        )

        return buildDefinition(
            id = annotation.id.trim().ifBlank { resolvedId },
            name = annotation.name.trim().ifBlank { annotation.id.trim().ifBlank { resolvedId } },
            schedule = schedule,
            concurrency = annotation.concurrency,
            serialQueueSize = annotation.serialQueueSize,
            serialOverflow = annotation.serialOverflow,
            misfire = annotation.misfire,
            catchUpLimit = annotation.catchUpLimit,
            dispatcher = annotation.dispatcher,
            enabled = annotation.enabled,
            executable = executable
        )
    }

    private fun buildDefinition(
        id: String,
        name: String,
        schedule: TaskSchedule,
        concurrency: AnnotationConcurrency,
        serialQueueSize: Int,
        serialOverflow: QueueOverflowPolicy,
        misfire: AnnotationMisfire,
        catchUpLimit: Int,
        dispatcher: String,
        enabled: Boolean,
        executable: TaskExecutable
    ): TaskDefinition {
        if (id.isBlank()) {
            throw TaskConfigurationException("Task id must not be blank")
        }
        if (name.isBlank()) {
            throw TaskConfigurationException("Task name must not be blank")
        }

        val concurrencyPolicy = when (concurrency) {
            AnnotationConcurrency.ALLOW -> ConcurrencyPolicy.Allow
            AnnotationConcurrency.SKIP_IF_RUNNING -> ConcurrencyPolicy.SkipIfRunning
            AnnotationConcurrency.QUEUE_ONE -> ConcurrencyPolicy.QueueOne
            AnnotationConcurrency.SERIAL -> {
                if (serialQueueSize <= 0) {
                    throw TaskConfigurationException("Task '$id' serialQueueSize must be positive: $serialQueueSize")
                }
                ConcurrencyPolicy.Serial(maxQueueSize = serialQueueSize, overflowPolicy = serialOverflow)
            }
        }

        val misfirePolicy = when (misfire) {
            AnnotationMisfire.SKIP -> MisfirePolicy.Skip
            AnnotationMisfire.FIRE_ONCE -> MisfirePolicy.FireOnce
            AnnotationMisfire.CATCH_UP -> {
                if (catchUpLimit <= 0) {
                    throw TaskConfigurationException("Task '$id' catchUpLimit must be positive: $catchUpLimit")
                }
                MisfirePolicy.CatchUp(maxExecutions = catchUpLimit)
            }
        }

        return TaskDefinition(
            id = id,
            name = name,
            schedule = schedule,
            concurrencyPolicy = concurrencyPolicy,
            misfirePolicy = misfirePolicy,
            dispatcherKey = dispatcher.trim().ifBlank { null },
            isEnabled = enabled,
            executable = executable
        )
    }
}

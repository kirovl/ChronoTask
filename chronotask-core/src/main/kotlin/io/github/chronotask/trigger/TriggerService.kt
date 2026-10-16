package io.github.chronotask.trigger

import io.github.chronotask.clock.ChronoClock
import io.github.chronotask.misfire.DefaultMisfireResolver
import io.github.chronotask.misfire.MisfireContext
import io.github.chronotask.misfire.MisfireResolver
import io.github.chronotask.misfire.MisfireResult
import io.github.chronotask.model.MisfirePolicy
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import java.time.Instant

/**
 * Service coordinating trigger timeline calculation and misfire resolution.
 *
 * Provides the unified scheduling entry point used by the scheduler engine:
 * 1. Computes the next planned trigger timestamp using [TriggerCalculator].
 * 2. If the planned timestamp is strictly before `now`, delegates to [MisfireResolver].
 * 3. If on-time or future, generates a standard [TriggerSource.SCHEDULE] trigger.
 */
class TriggerService @JvmOverloads constructor(
    val clock: ChronoClock = ChronoClock.SYSTEM,
    val misfireResolver: MisfireResolver = DefaultMisfireResolver
) {

    /**
     * Pure calculation of the next planned trigger instant without misfire resolution.
     */
    fun computeNextScheduledAt(schedule: TaskSchedule, context: TriggerContext): Instant? {
        val calculator = TriggerCalculatorFactory.create(schedule)
        return checkedNextTrigger(calculator, context)
    }

    private fun checkedNextTrigger(calculator: TriggerCalculator, context: TriggerContext): Instant? {
        val next = calculator.nextTrigger(context) ?: return null
        val previous = context.lastScheduledAt
        if (previous != null && !next.isAfter(previous)) {
            throw TriggerCalculationException(
                "Non-progressing schedule detected in TriggerCalculator: previous=$previous, next=$next"
            )
        }
        return next
    }

    /**
     * Evaluates the next scheduled triggers and advanced timeline for a [TaskDefinition] and execution history.
     */
    @JvmOverloads
    fun evaluate(
        task: TaskDefinition,
        registeredAt: Instant,
        lastScheduledAt: Instant? = null,
        lastStartedAt: Instant? = null,
        lastCompletedAt: Instant? = null,
        now: Instant = clock.now()
    ): MisfireResult {
        return evaluate(
            schedule = task.schedule,
            misfirePolicy = task.misfirePolicy,
            registeredAt = registeredAt,
            lastScheduledAt = lastScheduledAt,
            lastStartedAt = lastStartedAt,
            lastCompletedAt = lastCompletedAt,
            now = now
        )
    }

    /**
     * Evaluates the next scheduled triggers and advanced timeline for explicit parameters.
     */
    @JvmOverloads
    fun evaluate(
        schedule: TaskSchedule,
        misfirePolicy: MisfirePolicy,
        registeredAt: Instant,
        lastScheduledAt: Instant? = null,
        lastStartedAt: Instant? = null,
        lastCompletedAt: Instant? = null,
        now: Instant = clock.now()
    ): MisfireResult {
        val triggerContext = TriggerContext(
            now = now,
            registeredAt = registeredAt,
            lastScheduledAt = lastScheduledAt,
            lastStartedAt = lastStartedAt,
            lastCompletedAt = lastCompletedAt
        )
        val calculator = TriggerCalculatorFactory.create(schedule)
        val plannedInstant = checkedNextTrigger(calculator, triggerContext)
            ?: return MisfireResult(emptyList(), null)

        return if (plannedInstant.isBefore(now)) {
            val misfireContext = MisfireContext(
                schedule = schedule,
                misfirePolicy = misfirePolicy,
                triggerContext = triggerContext,
                firstScheduledAt = plannedInstant,
                now = now
            )
            misfireResolver.resolve(misfireContext)
        } else {
            MisfireResult(
                triggers = listOf(
                    ScheduledTrigger(
                        scheduledAt = plannedInstant,
                        discoveredAt = now,
                        source = TriggerSource.SCHEDULE
                    )
                ),
                nextScheduledAt = plannedInstant
            )
        }
    }
}

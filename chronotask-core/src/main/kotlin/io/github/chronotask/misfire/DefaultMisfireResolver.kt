package io.github.chronotask.misfire

import io.github.chronotask.model.MisfirePolicy
import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.cron.CronTriggerCalculator
import io.github.chronotask.trigger.ScheduledTrigger
import io.github.chronotask.trigger.TriggerCalculationException
import io.github.chronotask.trigger.TriggerCalculator
import io.github.chronotask.trigger.TriggerSource
import java.time.Duration
import java.time.Instant

/**
 * Default production implementation of [MisfireResolver].
 *
 * Implements anti-explosion mathematical jumps for FixedRate and deterministic resolution
 * across all schedule types according to ChronoTask Architecture Specification 1.0.1.
 */
object DefaultMisfireResolver : MisfireResolver {

    const val MAX_TRIGGER_SCAN_ITERATIONS: Int = 10_000

    override fun resolve(context: MisfireContext): MisfireResult {
        return when (val schedule = context.schedule) {
            is TaskSchedule.FixedRate -> resolveFixedRate(context, schedule)
            is TaskSchedule.FixedDelay -> resolveFixedDelay(context, schedule)
            is TaskSchedule.Once -> resolveOnce(context, schedule)
            is TaskSchedule.Custom -> resolveCalculatorDriven(context, schedule.calculator)
            is TaskSchedule.Cron -> resolveCron(context, CronTriggerCalculator(schedule))
        }
    }

    private fun resolveFixedRate(
        context: MisfireContext,
        schedule: TaskSchedule.FixedRate
    ): MisfireResult {
        val first = context.firstScheduledAt
        val interval = schedule.intervalMs
        val now = context.now

        // Count triggers strictly before now, retaining sub-millisecond precision.
        val diff = Duration.between(first, now).minusNanos(1).toMillis()
        val missedCount = diff / interval + 1
        val nextFutureInstant = first.plusMillis(missedCount * interval)
        val lastMissedInstant = first.plusMillis((missedCount - 1) * interval)

        return when (val policy = context.misfirePolicy) {
            is MisfirePolicy.Skip -> {
                MisfireResult(
                    triggers = emptyList(),
                    nextScheduledAt = nextFutureInstant,
                    alignedLastScheduledAt = lastMissedInstant
                )
            }
            is MisfirePolicy.FireOnce -> {
                MisfireResult(
                    triggers = listOf(
                        ScheduledTrigger(
                            scheduledAt = lastMissedInstant,
                            discoveredAt = now,
                            source = TriggerSource.MISFIRE
                        )
                    ),
                    nextScheduledAt = nextFutureInstant
                )
            }
            is MisfirePolicy.CatchUp -> {
                val toGenerate = minOf(missedCount, policy.maxExecutions.toLong()).toInt()
                val triggers = ArrayList<ScheduledTrigger>(toGenerate)
                for (k in 0 until toGenerate) {
                    triggers.add(
                        ScheduledTrigger(
                            scheduledAt = first.plusMillis(k * interval),
                            discoveredAt = now,
                            source = TriggerSource.MISFIRE
                        )
                    )
                }
                MisfireResult(
                    triggers = triggers,
                    nextScheduledAt = nextFutureInstant,
                    alignedLastScheduledAt = lastMissedInstant
                )
            }
        }
    }

    private fun resolveFixedDelay(
        context: MisfireContext,
        schedule: TaskSchedule.FixedDelay
    ): MisfireResult {
        val first = context.firstScheduledAt
        val now = context.now

        return when (context.misfirePolicy) {
            is MisfirePolicy.Skip -> {
                MisfireResult(
                    triggers = emptyList(),
                    nextScheduledAt = now.plusMillis(schedule.delayMs),
                    alignedLastScheduledAt = now
                )
            }
            is MisfirePolicy.FireOnce, is MisfirePolicy.CatchUp -> {
                MisfireResult(
                    triggers = listOf(
                        ScheduledTrigger(
                            scheduledAt = first,
                            discoveredAt = now,
                            source = TriggerSource.MISFIRE
                        )
                    ),
                    nextScheduledAt = null
                )
            }
        }
    }

    private fun resolveOnce(
        context: MisfireContext,
        schedule: TaskSchedule.Once
    ): MisfireResult {
        val first = context.firstScheduledAt
        val now = context.now

        return when (context.misfirePolicy) {
            is MisfirePolicy.Skip -> {
                MisfireResult(
                    triggers = emptyList(),
                    nextScheduledAt = null
                )
            }
            is MisfirePolicy.FireOnce, is MisfirePolicy.CatchUp -> {
                MisfireResult(
                    triggers = listOf(
                        ScheduledTrigger(
                            scheduledAt = first,
                            discoveredAt = now,
                            source = TriggerSource.MISFIRE
                        )
                    ),
                    nextScheduledAt = null
                )
            }
        }
    }

    private fun resolveCron(context: MisfireContext, calculator: CronTriggerCalculator): MisfireResult {
        fun nextAtOrAfter(instant: Instant): Instant? = calculator.nextTrigger(
            context.triggerContext.copy(lastScheduledAt = instant.minusNanos(1))
        )

        // Find the latest missed second without enumerating the entire offline interval.
        // A matching trigger at or after a second exists before now iff that second is
        // no later than the latest missed trigger, so this predicate supports binary search.
        var low = context.firstScheduledAt.epochSecond
        var high = context.now.minusNanos(1).epochSecond
        while (low < high) {
            val mid = low + (high - low + 1) / 2
            val candidate = nextAtOrAfter(Instant.ofEpochSecond(mid))
            if (candidate != null && candidate.isBefore(context.now)) {
                low = mid
            } else {
                high = mid - 1
            }
        }
        val lastMissed = Instant.ofEpochSecond(low)
        val nextPlanned = nextAtOrAfter(context.now)
        val triggers = when (val policy = context.misfirePolicy) {
            is MisfirePolicy.Skip -> emptyList()
            is MisfirePolicy.FireOnce -> listOf(
                ScheduledTrigger(lastMissed, context.now, TriggerSource.MISFIRE)
            )
            is MisfirePolicy.CatchUp -> {
                val missed = ArrayList<ScheduledTrigger>()
                var candidate: Instant? = context.firstScheduledAt
                while (candidate != null && candidate.isBefore(context.now) && missed.size < policy.maxExecutions) {
                    missed.add(ScheduledTrigger(candidate, context.now, TriggerSource.MISFIRE))
                    if (missed.size < policy.maxExecutions) {
                        candidate = calculator.nextTrigger(context.triggerContext.copy(lastScheduledAt = candidate))
                    }
                }
                missed
            }
        }
        return MisfireResult(triggers, nextPlanned, lastMissed)
    }

    private fun resolveCalculatorDriven(
        context: MisfireContext,
        calculator: TriggerCalculator
    ): MisfireResult {
        val missedList = ArrayList<Instant>()
        var currentMissed = context.firstScheduledAt
        missedList.add(currentMissed)
        var iterations = 0
        var nextPlanned: Instant? = null
        val now = context.now

        while (true) {
            if (++iterations > MAX_TRIGGER_SCAN_ITERATIONS) {
                throw TriggerCalculationException(
                    "Exceeded maximum scan iterations ($MAX_TRIGGER_SCAN_ITERATIONS) resolving schedule misfire"
                )
            }
            val simContext = context.triggerContext.copy(
                now = now,
                lastScheduledAt = currentMissed
            )
            val candidate = calculator.nextTrigger(simContext)
            if (candidate == null) {
                nextPlanned = null
                break
            }
            if (!candidate.isAfter(currentMissed)) {
                throw TriggerCalculationException(
                    "Non-progressing schedule detected in TriggerCalculator: previous=$currentMissed, next=$candidate"
                )
            }
            if (candidate.isBefore(now)) {
                missedList.add(candidate)
                currentMissed = candidate
            } else {
                nextPlanned = candidate
                break
            }
        }

        return when (val policy = context.misfirePolicy) {
            is MisfirePolicy.Skip -> {
                MisfireResult(
                    triggers = emptyList(),
                    nextScheduledAt = nextPlanned,
                    alignedLastScheduledAt = missedList.last()
                )
            }
            is MisfirePolicy.FireOnce -> {
                MisfireResult(
                    triggers = listOf(
                        ScheduledTrigger(
                            scheduledAt = missedList.last(),
                            discoveredAt = now,
                            source = TriggerSource.MISFIRE
                        )
                    ),
                    nextScheduledAt = nextPlanned
                )
            }
            is MisfirePolicy.CatchUp -> {
                val toTake = minOf(missedList.size, policy.maxExecutions)
                val triggers = ArrayList<ScheduledTrigger>(toTake)
                for (i in 0 until toTake) {
                    triggers.add(
                        ScheduledTrigger(
                            scheduledAt = missedList[i],
                            discoveredAt = now,
                            source = TriggerSource.MISFIRE
                        )
                    )
                }
                MisfireResult(
                    triggers = triggers,
                    nextScheduledAt = nextPlanned,
                    alignedLastScheduledAt = missedList.last()
                )
            }
        }
    }
}

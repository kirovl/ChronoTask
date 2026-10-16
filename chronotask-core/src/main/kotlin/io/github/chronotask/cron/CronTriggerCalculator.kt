package io.github.chronotask.cron

import io.github.chronotask.model.TaskSchedule
import io.github.chronotask.trigger.TriggerCalculator
import io.github.chronotask.trigger.TriggerContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * High-performance, anti-drift [TriggerCalculator] for [TaskSchedule.Cron].
 * Uses field-jumping search algorithm across year -> month -> day -> hour -> minute -> second.
 */
class CronTriggerCalculator(
    val schedule: TaskSchedule.Cron
) : TriggerCalculator {

    val cron: CronExpression = CronParser.parse(schedule.expression, schedule.dialect)
    val zoneId: ZoneId = schedule.zoneId

    override fun nextTrigger(context: TriggerContext): Instant? {
        val baseInstant = context.lastScheduledAt ?: context.registeredAt

        // Earliest possible candidate second strictly > baseInstant
        val startDateTime = ZonedDateTime.ofInstant(baseInstant, zoneId)
            .truncatedTo(ChronoUnit.SECONDS)
            .plusSeconds(1)

        var current = startDateTime
        fun advance(candidate: ZonedDateTime): ZonedDateTime {
            val transition = zoneId.rules.nextTransition(current.toInstant())
            // Field jumps must not skip repeated local times, including overlaps whose
            // duration is not a whole hour. Search again from the transition boundary.
            return if (transition != null && !transition.instant.isAfter(candidate.toInstant())) {
                transition.instant.atZone(zoneId)
            } else {
                candidate
            }
        }
        var iterations = 0
        val maxIterations = 10_000

        while (iterations < maxIterations) {
            iterations++

            // 1. Year check
            val year = current.year
            if (year > 2199) return null
            if (!cron.years.get(year)) {
                val nextYear = cron.years.nextSetBit(year)
                if (nextYear < 0 || nextYear > 2199) return null
                current = advance(current.withYear(nextYear).withMonth(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0))
                continue
            }

            // 2. Month check
            val month = current.monthValue
            if (!cron.months.get(month)) {
                val nextMonth = cron.months.nextSetBit(month)
                if (nextMonth < 0 || nextMonth > 12) {
                    current = advance(current.plusYears(1).withMonth(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0))
                } else {
                    current = advance(current.withMonth(nextMonth).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0))
                }
                continue
            }

            // 3. Day check (day-of-month and day-of-week)
            val lengthOfMonth = current.toLocalDate().lengthOfMonth()
            val dayOfMonth = current.dayOfMonth
            val quartzDow = if (current.dayOfWeek == DayOfWeek.SUNDAY) 1 else current.dayOfWeek.value + 1

            if (!cron.matchesDay(dayOfMonth, quartzDow, lengthOfMonth)) {
                current = advance(current.plusDays(1).withHour(0).withMinute(0).withSecond(0))
                continue
            }

            // 4. Hour check
            val hour = current.hour
            if (!cron.hours.get(hour)) {
                val nextHour = cron.hours.nextSetBit(hour)
                if (nextHour < 0 || nextHour > 23) {
                    current = advance(current.plusDays(1).withHour(0).withMinute(0).withSecond(0))
                } else {
                    current = advance(current.withHour(nextHour).withMinute(0).withSecond(0))
                }
                continue
            }

            // 5. Minute check
            val minute = current.minute
            if (!cron.minutes.get(minute)) {
                val nextMinute = cron.minutes.nextSetBit(minute)
                if (nextMinute < 0 || nextMinute > 59) {
                    current = advance(current.plusHours(1).withMinute(0).withSecond(0))
                } else {
                    current = advance(current.withMinute(nextMinute).withSecond(0))
                }
                continue
            }

            // 6. Second check
            val second = current.second
            if (!cron.seconds.get(second)) {
                val nextSecond = cron.seconds.nextSetBit(second)
                if (nextSecond < 0 || nextSecond > 59) {
                    current = advance(current.plusMinutes(1).withSecond(0))
                } else {
                    current = advance(current.withSecond(nextSecond))
                }
                continue
            }

            // Matched all fields, check DST transitions / overlaps
            val localDateTime = current.toLocalDateTime()
            val validOffsets = zoneId.rules.getValidOffsets(localDateTime)

            if (validOffsets.size > 1) {
                val earlierZdt = current.withEarlierOffsetAtOverlap()
                val earlierInstant = earlierZdt.toInstant()
                if (earlierInstant > baseInstant) {
                    return earlierInstant
                }
                val laterZdt = current.withLaterOffsetAtOverlap()
                val laterInstant = laterZdt.toInstant()
                if (laterInstant > baseInstant) {
                    return laterInstant
                }
                current = advance(current.plusSeconds(1))
                continue
            }

            val instant = current.toInstant()
            if (instant <= baseInstant) {
                current = advance(current.plusSeconds(1))
                continue
            }

            return instant
        }

        return null
    }
}

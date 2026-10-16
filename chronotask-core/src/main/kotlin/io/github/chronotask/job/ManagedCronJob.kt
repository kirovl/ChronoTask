package io.github.chronotask.job

import io.github.chronotask.cron.CronDialect
import io.github.chronotask.model.TaskSchedule
import java.time.ZoneId

/**
 * Convenience abstract [ChronoJob] specialized for Cron-scheduled tasks.
 *
 * @property id Unique task identifier.
 * @property cron Cron expression string.
 * @property zoneId Target timezone for resolving Cron field triggers.
 * @property dialect Syntax dialect (defaults to [CronDialect.QUARTZ]).
 */
abstract class ManagedCronJob @JvmOverloads constructor(
    final override val id: String,
    val cron: String,
    val zoneId: ZoneId,
    val dialect: CronDialect = CronDialect.QUARTZ
) : ChronoJob() {

    final override val schedule: TaskSchedule
        get() = TaskSchedule.Cron(
            expression = cron,
            zoneId = zoneId,
            dialect = dialect
        )
}

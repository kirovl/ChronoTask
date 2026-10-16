package io.github.chronotask.annotation

import io.github.chronotask.model.QueueOverflowPolicy

/**
 * Marks a method as a periodic scheduled task (FixedRate or FixedDelay).
 *
 * @property intervalMs Interval in milliseconds between executions.
 *                      In [ScheduleMode.FIXED_RATE], this is the period between trigger start times.
 *                      In [ScheduleMode.FIXED_DELAY], this is the delay between completion of one execution and start of the next.
 * @property initialDelayMs Delay in milliseconds before first execution trigger. Defaults to 0.
 * @property mode Schedule mode ([ScheduleMode.FIXED_RATE] or [ScheduleMode.FIXED_DELAY]). Defaults to [ScheduleMode.FIXED_RATE].
 * @property id Unique task identifier. If blank, defaults to `FQCN#methodName`.
 * @property name Human-readable task name. If blank, defaults to the resolved task ID.
 * @property concurrency Concurrency policy. Defaults to [AnnotationConcurrency.SKIP_IF_RUNNING].
 * @property misfire Misfire handling policy. Defaults to [AnnotationMisfire.SKIP].
 * @property catchUpLimit Maximum compensatory executions when misfire is [AnnotationMisfire.CATCH_UP]. Defaults to 100.
 * @property dispatcher Optional custom dispatcher key. Defaults to empty string (engine default dispatcher).
 * @property enabled Whether this task is enabled upon start. Defaults to true.
 * @property serialQueueSize Queue buffer capacity when concurrency is [AnnotationConcurrency.SERIAL]. Defaults to 1024.
 * @property serialOverflow Queue overflow handling strategy when concurrency is [AnnotationConcurrency.SERIAL]. Defaults to [QueueOverflowPolicy.DROP_OLDEST].
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Scheduled(
    val intervalMs: Long,
    val initialDelayMs: Long = 0,
    val mode: ScheduleMode = ScheduleMode.FIXED_RATE,
    val id: String = "",
    val name: String = "",
    val concurrency: AnnotationConcurrency = AnnotationConcurrency.SKIP_IF_RUNNING,
    val misfire: AnnotationMisfire = AnnotationMisfire.SKIP,
    val catchUpLimit: Int = 100,
    val dispatcher: String = "",
    val enabled: Boolean = true,
    val serialQueueSize: Int = 1024,
    val serialOverflow: QueueOverflowPolicy = QueueOverflowPolicy.DROP_OLDEST
)

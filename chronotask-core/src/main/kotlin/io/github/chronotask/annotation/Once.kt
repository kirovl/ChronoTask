package io.github.chronotask.annotation

import io.github.chronotask.model.QueueOverflowPolicy

/**
 * Marks a method as a one-time execution task after an optional delay.
 *
 * @property delayMs Initial delay in milliseconds before the one-time trigger. Defaults to 0 (immediate execution).
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
annotation class Once(
    val delayMs: Long = 0,
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

package io.github.chronotask.engine

import io.github.chronotask.clock.ChronoClock
import io.github.chronotask.clock.ChronoTimer
import io.github.chronotask.execution.DefaultTaskRunner
import io.github.chronotask.execution.ExecutionListener
import io.github.chronotask.execution.TaskErrorHandler
import io.github.chronotask.execution.TaskRunner
import io.github.chronotask.misfire.DefaultMisfireResolver
import io.github.chronotask.misfire.MisfireResolver

/**
 * Configuration parameters and pluggable collaborators for a [ChronoTaskEngine].
 */
data class EngineConfig @JvmOverloads constructor(
    val clock: ChronoClock = ChronoClock.SYSTEM,
    val timer: ChronoTimer = ChronoTimer.SYSTEM,
    val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
    val lifecycleListener: TaskLifecycleListener? = null,
    val executionListener: ExecutionListener? = null,
    val errorHandler: TaskErrorHandler? = null,
    val runner: TaskRunner = DefaultTaskRunner,
    val misfireResolver: MisfireResolver = DefaultMisfireResolver
)

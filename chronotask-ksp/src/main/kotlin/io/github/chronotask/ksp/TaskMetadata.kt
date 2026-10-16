package io.github.chronotask.ksp

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSTypeParameter
import io.github.chronotask.cron.CronDialect
import io.github.chronotask.model.QueueOverflowPolicy

internal sealed interface TaskScheduleMeta {
    data class FixedRate(val intervalMs: Long, val initialDelayMs: Long) : TaskScheduleMeta
    data class FixedDelay(val intervalMs: Long, val initialDelayMs: Long) : TaskScheduleMeta
    data class Cron(val expression: String, val zone: String, val dialect: CronDialect) : TaskScheduleMeta
    data class Once(val delayMs: Long) : TaskScheduleMeta
}

internal sealed interface ConcurrencyMeta {
    data object Allow : ConcurrencyMeta
    data object SkipIfRunning : ConcurrencyMeta
    data object QueueOne : ConcurrencyMeta
    data class Serial(val queueSize: Int, val overflow: QueueOverflowPolicy) : ConcurrencyMeta
}

internal sealed interface MisfireMeta {
    data object Skip : MisfireMeta
    data object FireOnce : MisfireMeta
    data class CatchUp(val catchUpLimit: Int) : MisfireMeta
}

internal data class TaskMethodMeta(
    val methodName: String,
    val isSuspend: Boolean,
    val hasContextParam: Boolean,
    val declaringClassFqcn: String,
    val taskId: String,
    val taskName: String,
    val dispatcherKey: String?,
    val isEnabled: Boolean,
    val schedule: TaskScheduleMeta,
    val concurrency: ConcurrencyMeta,
    val misfire: MisfireMeta,
    val originatingFile: KSFile?
)

internal data class TargetClassMeta(
    val ksClass: KSClassDeclaration,
    val packageName: String,
    val simpleName: String,
    val bindingSimpleName: String,
    val typeParameters: List<KSTypeParameter>,
    val tasks: List<TaskMethodMeta>,
    val originatingFiles: List<KSFile>,
    val isInternal: Boolean = false
)

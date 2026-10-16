package io.github.chronotask.processor

import io.github.chronotask.annotation.AnnotationConcurrency
import io.github.chronotask.annotation.AnnotationMisfire
import io.github.chronotask.annotation.Cron
import io.github.chronotask.annotation.Once
import io.github.chronotask.annotation.ScheduleMode
import io.github.chronotask.annotation.Scheduled
import io.github.chronotask.annotation.TickTask
import io.github.chronotask.cron.CronDialect
import io.github.chronotask.cron.CronParser
import io.github.chronotask.model.QueueOverflowPolicy
import java.time.ZoneId
import javax.annotation.processing.Messager
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.NestingKind
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import javax.tools.Diagnostic

internal class TaskMetadataParser(
    private val elementUtils: Elements,
    private val typeUtils: Types,
    private val messager: Messager
) {

    fun parseTargetClass(typeElement: TypeElement): TargetClassMeta? {
        if (typeElement.kind != ElementKind.CLASS) {
            return null
        }

        if (typeElement.nestingKind == NestingKind.ANONYMOUS || typeElement.nestingKind == NestingKind.LOCAL) {
            messager.printMessage(
                Diagnostic.Kind.ERROR,
                "ChronoTask: Local or anonymous classes cannot contain scheduled tasks: '${typeElement.qualifiedName}'",
                typeElement
            )
            return null
        }

        val packageName = elementUtils.getPackageOf(typeElement).qualifiedName.toString()
        val simpleName = typeElement.simpleName.toString()
        val bindingSimpleName = computeBindingSimpleName(typeElement)

        if (simpleName.endsWith("_ChronoTaskBinding")) {
            messager.printMessage(
                Diagnostic.Kind.ERROR,
                "ChronoTask: Class name '$simpleName' ends with reserved suffix '_ChronoTaskBinding'.",
                typeElement
            )
            return null
        }

        val originatingElements = mutableListOf<Element>()
        originatingElements.add(typeElement)

        val tasks = mutableListOf<TaskMethodMeta>()
        val inheritedMembers = elementUtils.getAllMembers(typeElement).toSet()
        val seenSignatures = mutableSetOf<String>()
        val seenTaskIds = mutableSetOf<String>()
        var hadValidationError = false

        var currentType: TypeElement? = typeElement
        while (currentType != null && currentType.qualifiedName.toString() != "java.lang.Object") {
            originatingElements.add(currentType)

            for (enclosed in currentType.enclosedElements) {
                if (enclosed.kind != ElementKind.METHOD) continue
                val method = enclosed as ExecutableElement
                // An ancestor declaration may not be inherited (e.g. package-private
                // across packages), even when its declaring class is accessible.
                if (currentType != typeElement && method !in inheritedMembers) continue

                val signature = method.simpleName.toString() + "(" + method.parameters.joinToString(",") {
                    typeUtils.erasure(it.asType()).toString()
                } + ")"

                if (signature in seenSignatures) {
                    // Subclass override suppression: already handled in subclass
                    continue
                }
                seenSignatures.add(signature)

                val scheduledAnn = method.getAnnotation(Scheduled::class.java)
                val cronAnn = method.getAnnotation(Cron::class.java)
                val onceAnn = method.getAnnotation(Once::class.java)
                val tickTaskAnn = method.getAnnotation(TickTask::class.java)

                val annotationsCount = listOfNotNull(scheduledAnn, cronAnn, onceAnn, tickTaskAnn).size
                if (annotationsCount == 0) {
                    continue
                }

                val methodRef = "${currentType.qualifiedName}#${method.simpleName}"

                if (annotationsCount > 1) {
                    val names = listOfNotNull(
                        if (scheduledAnn != null) "Scheduled" else null,
                        if (cronAnn != null) "Cron" else null,
                        if (onceAnn != null) "Once" else null,
                        if (tickTaskAnn != null) "TickTask" else null
                    ).joinToString(", ")
                    messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "ChronoTask: Method '$methodRef' has multiple scheduling annotations ($names). Exactly one scheduling annotation is allowed per method.",
                        method
                    )
                    hadValidationError = true
                    continue
                }

                if (method.modifiers.contains(Modifier.ABSTRACT)) {
                    messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "ChronoTask: Annotated method '$methodRef' must not be abstract.",
                        method
                    )
                    hadValidationError = true
                    continue
                }

                if (method.modifiers.contains(Modifier.STATIC)) {
                    messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "ChronoTask: Annotated method '$methodRef' must not be static.",
                        method
                    )
                    hadValidationError = true
                    continue
                }

                if (method.modifiers.contains(Modifier.PROTECTED)) {
                    messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "ChronoTask: Annotated method '$methodRef' has unsupported visibility (protected). In Java, annotated methods must be public or package-private.",
                        method
                    )
                    hadValidationError = true
                    continue
                }

                if (method.modifiers.contains(Modifier.PRIVATE)) {
                    messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "ChronoTask: Annotated method '$methodRef' has unsupported visibility (private). Annotated methods must be public or package-private.",
                        method
                    )
                    hadValidationError = true
                    continue
                }

                if (method.returnType.kind != TypeKind.VOID) {
                    messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "ChronoTask: Annotated method '$methodRef' must return void, but returns '${method.returnType}'.",
                        method
                    )
                    hadValidationError = true
                    continue
                }

                val params = method.parameters
                val hasContextParam: Boolean
                if (params.isEmpty()) {
                    hasContextParam = false
                } else if (params.size == 1) {
                    val paramTypeStr = typeUtils.erasure(params[0].asType()).toString()
                    val isContext = paramTypeStr == "io.github.chronotask.job.TaskExecutionContext" ||
                            paramTypeStr == "io.github.chronotask.execution.TaskExecutionContext"
                    if (!isContext) {
                        messager.printMessage(
                            Diagnostic.Kind.ERROR,
                            "ChronoTask: Annotated method '$methodRef' has invalid parameters ($paramTypeStr). Expected () or (TaskExecutionContext).",
                            method
                        )
                        hadValidationError = true
                        continue
                    }
                    hasContextParam = true
                } else {
                    val paramNames = params.joinToString { it.asType().toString() }
                    messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "ChronoTask: Annotated method '$methodRef' has invalid parameter count (${params.size}: $paramNames). Expected () or (TaskExecutionContext).",
                        method
                    )
                    hadValidationError = true
                    continue
                }

                val parsedMethod = parseTaskMethod(
                    method = method,
                    scheduledAnn = scheduledAnn,
                    cronAnn = cronAnn,
                    onceAnn = onceAnn,
                    tickTaskAnn = tickTaskAnn,
                    methodRef = methodRef,
                    declaringClassFqcn = currentType.qualifiedName.toString(),
                    hasContextParam = hasContextParam
                )

                if (parsedMethod == null) {
                    hadValidationError = true
                    continue
                }

                if (parsedMethod.taskId in seenTaskIds) {
                    messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "ChronoTask: Duplicate task ID '${parsedMethod.taskId}' detected on method '$methodRef'.",
                        method
                    )
                    hadValidationError = true
                    continue
                }
                seenTaskIds.add(parsedMethod.taskId)
                tasks.add(parsedMethod)
            }

            val superclassMirror = currentType.superclass
            if (superclassMirror is DeclaredType) {
                currentType = superclassMirror.asElement() as? TypeElement
            } else {
                currentType = null
            }
        }

        if (hadValidationError || tasks.isEmpty()) {
            return null
        }

        val sortedTasks = tasks.sortedWith(
            compareBy<TaskMethodMeta> { it.declaringClassFqcn }
                .thenBy { it.methodName }
                .thenBy { it.taskId }
        )

        return TargetClassMeta(
            typeElement = typeElement,
            packageName = packageName,
            simpleName = simpleName,
            bindingSimpleName = bindingSimpleName,
            typeParameters = typeElement.typeParameters.toList(),
            tasks = sortedTasks,
            originatingElements = originatingElements.distinct()
        )
    }

    private fun computeBindingSimpleName(typeElement: TypeElement): String {
        val names = mutableListOf<String>()
        var curr: Element? = typeElement
        while (curr is TypeElement) {
            names.add(0, curr.simpleName.toString())
            curr = curr.enclosingElement
        }
        // Keep existing names when no component contains the nesting separator.
        // Otherwise use a reserved prefix and length-delimited components: escaping
        // underscores alone cannot distinguish e.g. Flat_.Name from Flat._Name.
        val bindingName = if (names.any { '_' in it }) {
            "__" + names.joinToString("_") { "${it.length}_$it" }
        } else {
            names.joinToString("_")
        }
        return bindingName + "_ChronoTaskBinding"
    }

    private fun parseTaskMethod(
        method: ExecutableElement,
        scheduledAnn: Scheduled?,
        cronAnn: Cron?,
        onceAnn: Once?,
        tickTaskAnn: TickTask?,
        methodRef: String,
        declaringClassFqcn: String,
        hasContextParam: Boolean
    ): TaskMethodMeta? {
        val explicitId: String
        val explicitName: String
        val explicitDispatcher: String
        val isEnabled: Boolean
        val catchUpLimit: Int
        val serialQueueSize: Int
        val serialOverflow: QueueOverflowPolicy
        val concurrencyRaw: AnnotationConcurrency
        val misfireRaw: AnnotationMisfire
        val schedule: TaskScheduleMeta

        when {
            scheduledAnn != null -> {
                explicitId = scheduledAnn.id.trim()
                explicitName = scheduledAnn.name.trim()
                explicitDispatcher = scheduledAnn.dispatcher.trim()
                isEnabled = scheduledAnn.enabled
                catchUpLimit = scheduledAnn.catchUpLimit
                serialQueueSize = scheduledAnn.serialQueueSize
                serialOverflow = scheduledAnn.serialOverflow
                concurrencyRaw = scheduledAnn.concurrency
                misfireRaw = scheduledAnn.misfire

                if (scheduledAnn.intervalMs <= 0L) {
                    messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "ChronoTask: Method '$methodRef' has invalid intervalMs: ${scheduledAnn.intervalMs}. Must be > 0.",
                        method
                    )
                    return null
                }
                if (scheduledAnn.initialDelayMs < 0L) {
                    messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "ChronoTask: Method '$methodRef' has invalid initialDelayMs: ${scheduledAnn.initialDelayMs}. Must be >= 0.",
                        method
                    )
                    return null
                }

                schedule = if (scheduledAnn.mode == ScheduleMode.FIXED_DELAY) {
                    TaskScheduleMeta.FixedDelay(scheduledAnn.intervalMs, scheduledAnn.initialDelayMs)
                } else {
                    TaskScheduleMeta.FixedRate(scheduledAnn.intervalMs, scheduledAnn.initialDelayMs)
                }
            }
            cronAnn != null -> {
                explicitId = cronAnn.id.trim()
                explicitName = cronAnn.name.trim()
                explicitDispatcher = cronAnn.dispatcher.trim()
                isEnabled = cronAnn.enabled
                catchUpLimit = cronAnn.catchUpLimit
                serialQueueSize = cronAnn.serialQueueSize
                serialOverflow = cronAnn.serialOverflow
                concurrencyRaw = cronAnn.concurrency
                misfireRaw = cronAnn.misfire

                val expression = cronAnn.expression.trim()
                if (expression.isBlank()) {
                    messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "ChronoTask: Cron expression on method '$methodRef' must not be blank.",
                        method
                    )
                    return null
                }

                val zone = cronAnn.zone.trim()
                try {
                    ZoneId.of(zone)
                } catch (e: Exception) {
                    messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "ChronoTask: Method '$methodRef' has invalid timezone '$zone': ${e.message}",
                        method
                    )
                    return null
                }

                try {
                    CronParser.parse(expression, cronAnn.dialect)
                } catch (e: Exception) {
                    messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "ChronoTask: Method '$methodRef' has invalid Cron expression '$expression' (${cronAnn.dialect.name}): ${e.message}",
                        method
                    )
                    return null
                }

                schedule = TaskScheduleMeta.Cron(expression, zone, cronAnn.dialect)
            }
            onceAnn != null -> {
                explicitId = onceAnn.id.trim()
                explicitName = onceAnn.name.trim()
                explicitDispatcher = onceAnn.dispatcher.trim()
                isEnabled = onceAnn.enabled
                catchUpLimit = onceAnn.catchUpLimit
                serialQueueSize = onceAnn.serialQueueSize
                serialOverflow = onceAnn.serialOverflow
                concurrencyRaw = onceAnn.concurrency
                misfireRaw = onceAnn.misfire

                if (onceAnn.delayMs < 0L) {
                    messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "ChronoTask: Method '$methodRef' has invalid delayMs: ${onceAnn.delayMs}. Must be >= 0.",
                        method
                    )
                    return null
                }

                schedule = TaskScheduleMeta.Once(onceAnn.delayMs)
            }
            tickTaskAnn != null -> {
                explicitId = tickTaskAnn.id.trim()
                explicitName = tickTaskAnn.name.trim()
                explicitDispatcher = tickTaskAnn.dispatcher.trim()
                isEnabled = tickTaskAnn.enabled
                catchUpLimit = tickTaskAnn.catchUpLimit
                serialQueueSize = tickTaskAnn.serialQueueSize
                serialOverflow = tickTaskAnn.serialOverflow
                concurrencyRaw = tickTaskAnn.concurrency
                misfireRaw = tickTaskAnn.misfire

                if (tickTaskAnn.intervalMs <= 0L) {
                    messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "ChronoTask: Method '$methodRef' has invalid intervalMs: ${tickTaskAnn.intervalMs}. Must be > 0.",
                        method
                    )
                    return null
                }
                if (tickTaskAnn.initialDelayMs < 0L) {
                    messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "ChronoTask: Method '$methodRef' has invalid initialDelayMs: ${tickTaskAnn.initialDelayMs}. Must be >= 0.",
                        method
                    )
                    return null
                }

                schedule = TaskScheduleMeta.FixedRate(tickTaskAnn.intervalMs, tickTaskAnn.initialDelayMs)
            }
            else -> return null
        }

        if (catchUpLimit <= 0) {
            messager.printMessage(
                Diagnostic.Kind.ERROR,
                "ChronoTask: Method '$methodRef' has invalid catchUpLimit: $catchUpLimit. Must be > 0.",
                method
            )
            return null
        }

        if (serialQueueSize <= 0) {
            messager.printMessage(
                Diagnostic.Kind.ERROR,
                "ChronoTask: Method '$methodRef' has invalid serialQueueSize: $serialQueueSize. Must be > 0.",
                method
            )
            return null
        }

        val taskId = explicitId.ifBlank { methodRef }
        val taskName = explicitName.ifBlank { taskId }
        val dispatcherKey = explicitDispatcher.ifBlank { null }

        val concurrency = when (concurrencyRaw) {
            AnnotationConcurrency.ALLOW -> ConcurrencyMeta.Allow
            AnnotationConcurrency.SKIP_IF_RUNNING -> ConcurrencyMeta.SkipIfRunning
            AnnotationConcurrency.QUEUE_ONE -> ConcurrencyMeta.QueueOne
            AnnotationConcurrency.SERIAL -> ConcurrencyMeta.Serial(serialQueueSize, serialOverflow)
        }

        val misfire = when (misfireRaw) {
            AnnotationMisfire.SKIP -> MisfireMeta.Skip
            AnnotationMisfire.FIRE_ONCE -> MisfireMeta.FireOnce
            AnnotationMisfire.CATCH_UP -> MisfireMeta.CatchUp(catchUpLimit)
        }

        return TaskMethodMeta(
            methodName = method.simpleName.toString(),
            hasContextParam = hasContextParam,
            declaringClassFqcn = declaringClassFqcn,
            taskId = taskId,
            taskName = taskName,
            dispatcherKey = dispatcherKey,
            isEnabled = isEnabled,
            schedule = schedule,
            concurrency = concurrency,
            misfire = misfire,
            originatingElement = method
        )
    }
}

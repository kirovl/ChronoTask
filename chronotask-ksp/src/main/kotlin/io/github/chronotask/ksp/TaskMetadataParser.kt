package io.github.chronotask.ksp

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.isLocal
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Visibility
import io.github.chronotask.cron.CronDialect
import io.github.chronotask.cron.CronParser
import io.github.chronotask.model.QueueOverflowPolicy
import java.time.ZoneId

internal class TaskMetadataParser(
    private val logger: KSPLogger
) {

    private val scheduledFqcn = "io.github.chronotask.annotation.Scheduled"
    private val cronFqcn = "io.github.chronotask.annotation.Cron"
    private val onceFqcn = "io.github.chronotask.annotation.Once"
    private val tickTaskFqcn = "io.github.chronotask.annotation.TickTask"

    private val supportedAnnotationFqcns = setOf(scheduledFqcn, cronFqcn, onceFqcn, tickTaskFqcn)

    private data class FunctionSignature(
        val name: String,
        val paramTypes: List<String>,
        val extensionReceiverType: String?
    )

    fun parseTargetClass(ksClass: KSClassDeclaration): TargetClassMeta? {
        if (ksClass.classKind != ClassKind.CLASS && ksClass.classKind != ClassKind.OBJECT) {
            return null
        }

        // Check if class is local or anonymous
        if (ksClass.isLocal()) {
            logger.error("ChronoTask: Local classes cannot contain scheduled tasks: '${ksClass.qualifiedName?.asString()}'", ksClass)
            return null
        }

        var isInternal = false
        var enclosingClass: KSClassDeclaration? = ksClass
        while (enclosingClass != null) {
            when (val visibility = enclosingClass.getVisibility()) {
                Visibility.PUBLIC -> Unit
                Visibility.INTERNAL -> isInternal = true
                else -> {
                    logger.error(
                        "ChronoTask: Task class '${ksClass.qualifiedName?.asString()}' is inaccessible to its generated binding: " +
                            "'${enclosingClass.qualifiedName?.asString()}' has visibility $visibility. " +
                            "Task classes and their enclosing classes must be public or internal.",
                        ksClass
                    )
                    return null
                }
            }
            enclosingClass = enclosingClass.parentDeclaration as? KSClassDeclaration
        }

        val packageName = ksClass.packageName.asString()
        val simpleName = ksClass.simpleName.asString()
        val bindingSimpleName = computeBindingSimpleName(ksClass)

        // Check for reserved name collision
        if (simpleName.endsWith("_ChronoTaskBinding")) {
            logger.error("ChronoTask: Class name '$simpleName' ends with reserved suffix '_ChronoTaskBinding'.", ksClass)
            return null
        }

        val originatingFiles = mutableSetOf<KSFile>()
        ksClass.containingFile?.let { originatingFiles.add(it) }

        val tasks = mutableListOf<TaskMethodMeta>()
        val seenSignatures = mutableSetOf<FunctionSignature>()
        val seenTaskIds = mutableSetOf<String>()

        // Walk class hierarchy from concrete class to superclasses
        var currentClass: KSClassDeclaration? = ksClass
        while (currentClass != null && currentClass.qualifiedName?.asString() != "kotlin.Any") {
            currentClass.containingFile?.let { originatingFiles.add(it) }

            val declaredFunctions = currentClass.getDeclaredFunctions().toList()
            for (function in declaredFunctions) {
                val signature = FunctionSignature(
                    name = function.simpleName.asString(),
                    paramTypes = function.parameters.map { it.type.resolve().expandAliases().declaration.qualifiedName?.asString() ?: "" },
                    // A member extension does not override a regular member with the same
                    // name and value parameters; preserve that distinction while scanning.
                    extensionReceiverType = function.extensionReceiver?.let {
                        it.resolve().expandAliases().declaration.qualifiedName?.asString() ?: ""
                    }
                )

                if (signature in seenSignatures) {
                    // Method already processed in subclass
                    // If subclass did not annotate it, base annotation is suppressed.
                    // If subclass annotated it, it was already processed in subclass.
                    continue
                }
                seenSignatures.add(signature)

                val annotations = function.annotations.filter { ann ->
                    val fqcn = ann.annotationType.resolve().declaration.qualifiedName?.asString()
                    fqcn in supportedAnnotationFqcns
                }.toList()

                if (annotations.isEmpty()) {
                    continue
                }

                val methodRef = "${currentClass.qualifiedName?.asString()}#${function.simpleName.asString()}"

                // 1. Multiple scheduling annotations constraint
                if (annotations.size > 1) {
                    val names = annotations.joinToString { it.shortName.asString() }
                    logger.error(
                        "ChronoTask: Method '$methodRef' has multiple scheduling annotations ($names). Exactly one scheduling annotation is allowed per method.",
                        function
                    )
                    continue
                }

                val annotation = annotations.single()

                // 2. Modifier & Visibility constraints
                if (function.typeParameters.isNotEmpty()) {
                    logger.error(
                        "ChronoTask: Annotated method '$methodRef' must not declare type parameters. Generic task methods are not supported.",
                        function
                    )
                    continue
                }
                if (function.modifiers.contains(Modifier.ABSTRACT)) {
                    logger.error("ChronoTask: Annotated method '$methodRef' must not be abstract.", function)
                    continue
                }
                if (function.extensionReceiver != null) {
                    logger.error("ChronoTask: Annotated method '$methodRef' must not be an extension function.", function)
                    continue
                }
                if (function.annotations.any { it.shortName.asString() == "JvmStatic" }) {
                    logger.error("ChronoTask: Annotated method '$methodRef' must not be static or @JvmStatic.", function)
                    continue
                }

                val visibility = function.getVisibility()
                if (visibility != Visibility.PUBLIC && visibility != Visibility.INTERNAL) {
                    logger.error(
                        "ChronoTask: Annotated method '$methodRef' has unsupported visibility ($visibility). Annotated methods must be public or internal.",
                        function
                    )
                    continue
                }

                // 3. Return Type validation
                val returnType = function.returnType?.resolve()?.expandAliases()
                if (returnType?.declaration?.qualifiedName?.asString() != "kotlin.Unit") {
                    logger.error(
                        "ChronoTask: Annotated method '$methodRef' must return Unit, but returns '${returnType?.declaration?.qualifiedName?.asString()}'.",
                        function
                    )
                    continue
                }

                // 4. Parameter Signature validation
                val params = function.parameters
                val hasContextParam: Boolean
                if (params.isEmpty()) {
                    hasContextParam = false
                } else if (params.size == 1) {
                    val paramType = params[0].type.resolve().expandAliases()
                    val paramFqcn = paramType.declaration.qualifiedName?.asString()
                    val isContext = paramFqcn == "io.github.chronotask.job.TaskExecutionContext" ||
                            paramFqcn == "io.github.chronotask.execution.TaskExecutionContext"
                    if (!isContext || paramType.isMarkedNullable) {
                        logger.error(
                            "ChronoTask: Annotated method '$methodRef' has invalid parameters (${paramFqcn}${if (paramType.isMarkedNullable) "?" else ""}). Expected () or (TaskExecutionContext).",
                            function
                        )
                        continue
                    }
                    hasContextParam = true
                } else {
                    val paramNames = params.joinToString { it.type.resolve().expandAliases().declaration.qualifiedName?.asString() ?: "" }
                    logger.error(
                        "ChronoTask: Annotated method '$methodRef' has invalid parameter count (${params.size}: $paramNames). Expected () or (TaskExecutionContext).",
                        function
                    )
                    continue
                }

                // 5. Parse Annotation parameters and build metadata
                val annFqcn = annotation.annotationType.resolve().declaration.qualifiedName?.asString() ?: ""
                val parsedMethod = parseTaskMethod(
                    function = function,
                    annotation = annotation,
                    annFqcn = annFqcn,
                    methodRef = methodRef,
                    declaringClassFqcn = currentClass.qualifiedName?.asString() ?: "",
                    hasContextParam = hasContextParam
                ) ?: continue

                // 6. Duplicate Task ID detection within current class
                if (parsedMethod.taskId in seenTaskIds) {
                    logger.error(
                        "ChronoTask: Duplicate task ID '${parsedMethod.taskId}' detected on method '$methodRef'.",
                        function
                    )
                    continue
                }
                seenTaskIds.add(parsedMethod.taskId)
                tasks.add(parsedMethod)
            }

            // Move to superclass
            val superType = currentClass.superTypes
                .map { it.resolve() }
                .firstOrNull { (it.declaration as? KSClassDeclaration)?.classKind == ClassKind.CLASS }
            currentClass = superType?.declaration as? KSClassDeclaration
        }

        if (tasks.isEmpty()) {
            return null
        }

        // Deterministic sorting of task methods by declaringClassFqcn, methodName, and signature
        val sortedTasks = tasks.sortedWith(
            compareBy<TaskMethodMeta> { it.declaringClassFqcn }
                .thenBy { it.methodName }
                .thenBy { it.taskId }
        )

        return TargetClassMeta(
            ksClass = ksClass,
            packageName = packageName,
            simpleName = simpleName,
            bindingSimpleName = bindingSimpleName,
            typeParameters = ksClass.typeParameters,
            tasks = sortedTasks,
            originatingFiles = originatingFiles.toList(),
            isInternal = isInternal
        )
    }

    // KSP exposes the alias declaration rather than its underlying class. Preserve
    // nullability from both the use site and every layer of an alias chain.
    private fun KSType.expandAliases(): KSType {
        val alias = declaration as? KSTypeAlias ?: return this
        val expanded = alias.type.resolve().expandAliases()
        return if (isMarkedNullable) expanded.makeNullable() else expanded
    }

    private fun computeBindingSimpleName(ksClass: KSClassDeclaration): String {
        val names = mutableListOf<String>()
        var curr: KSClassDeclaration? = ksClass
        while (curr != null) {
            names.add(0, curr.simpleName.asString())
            curr = curr.parentDeclaration as? KSClassDeclaration
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
        function: KSFunctionDeclaration,
        annotation: KSAnnotation,
        annFqcn: String,
        methodRef: String,
        declaringClassFqcn: String,
        hasContextParam: Boolean
    ): TaskMethodMeta? {
        val isSuspend = function.modifiers.contains(Modifier.SUSPEND)
        val args = annotation.arguments.associate { (it.name?.asString() ?: "") to it.value }

        val explicitId = (args["id"] as? String)?.trim() ?: ""
        val taskId = explicitId.ifBlank { methodRef }

        val explicitName = (args["name"] as? String)?.trim() ?: ""
        val taskName = explicitName.ifBlank { taskId }

        val explicitDispatcher = (args["dispatcher"] as? String)?.trim() ?: ""
        val dispatcherKey = explicitDispatcher.ifBlank { null }

        val isEnabled = args["enabled"] as? Boolean ?: true

        val catchUpLimit = (args["catchUpLimit"] as? Int) ?: 100
        if (catchUpLimit <= 0) {
            logger.error("ChronoTask: Method '$methodRef' has invalid catchUpLimit: $catchUpLimit. Must be > 0.", function)
            return null
        }

        val serialQueueSize = (args["serialQueueSize"] as? Int) ?: 1024
        if (serialQueueSize <= 0) {
            logger.error("ChronoTask: Method '$methodRef' has invalid serialQueueSize: $serialQueueSize. Must be > 0.", function)
            return null
        }

        val serialOverflow = parseSerialOverflow(args["serialOverflow"])

        val concurrency = parseConcurrency(args["concurrency"], serialQueueSize, serialOverflow)
        val misfire = parseMisfire(args["misfire"], catchUpLimit)

        val schedule = when (annFqcn) {
            scheduledFqcn -> {
                val intervalMs = (args["intervalMs"] as? Long) ?: 0L
                if (intervalMs <= 0L) {
                    logger.error("ChronoTask: Method '$methodRef' has invalid intervalMs: $intervalMs. Must be > 0.", function)
                    return null
                }
                val initialDelayMs = (args["initialDelayMs"] as? Long) ?: 0L
                if (initialDelayMs < 0L) {
                    logger.error("ChronoTask: Method '$methodRef' has invalid initialDelayMs: $initialDelayMs. Must be >= 0.", function)
                    return null
                }
                val modeStr = extractEnumName(args["mode"]) ?: "FIXED_RATE"
                if (modeStr == "FIXED_DELAY") {
                    TaskScheduleMeta.FixedDelay(intervalMs, initialDelayMs)
                } else {
                    TaskScheduleMeta.FixedRate(intervalMs, initialDelayMs)
                }
            }
            cronFqcn -> {
                val expression = (args["expression"] as? String)?.trim() ?: ""
                if (expression.isBlank()) {
                    logger.error("ChronoTask: Cron expression on method '$methodRef' must not be blank.", function)
                    return null
                }
                val zone = (args["zone"] as? String)?.trim() ?: "UTC"
                try {
                    ZoneId.of(zone)
                } catch (e: Exception) {
                    logger.error("ChronoTask: Method '$methodRef' has invalid timezone '$zone': ${e.message}", function)
                    return null
                }
                val dialectStr = extractEnumName(args["dialect"]) ?: "QUARTZ"
                val dialect = if (dialectStr == "UNIX") CronDialect.UNIX else CronDialect.QUARTZ

                try {
                    CronParser.parse(expression, dialect)
                } catch (e: Exception) {
                    logger.error("ChronoTask: Method '$methodRef' has invalid Cron expression '$expression' (${dialect.name}): ${e.message}", function)
                    return null
                }

                TaskScheduleMeta.Cron(expression, zone, dialect)
            }
            onceFqcn -> {
                val delayMs = (args["delayMs"] as? Long) ?: 0L
                if (delayMs < 0L) {
                    logger.error("ChronoTask: Method '$methodRef' has invalid delayMs: $delayMs. Must be >= 0.", function)
                    return null
                }
                TaskScheduleMeta.Once(delayMs)
            }
            tickTaskFqcn -> {
                val intervalMs = (args["intervalMs"] as? Long) ?: 0L
                if (intervalMs <= 0L) {
                    logger.error("ChronoTask: Method '$methodRef' has invalid intervalMs: $intervalMs. Must be > 0.", function)
                    return null
                }
                val initialDelayMs = (args["initialDelayMs"] as? Long) ?: 0L
                if (initialDelayMs < 0L) {
                    logger.error("ChronoTask: Method '$methodRef' has invalid initialDelayMs: $initialDelayMs. Must be >= 0.", function)
                    return null
                }
                TaskScheduleMeta.FixedRate(intervalMs, initialDelayMs)
            }
            else -> return null
        }

        return TaskMethodMeta(
            methodName = function.simpleName.asString(),
            isSuspend = isSuspend,
            hasContextParam = hasContextParam,
            declaringClassFqcn = declaringClassFqcn,
            taskId = taskId,
            taskName = taskName,
            dispatcherKey = dispatcherKey,
            isEnabled = isEnabled,
            schedule = schedule,
            concurrency = concurrency,
            misfire = misfire,
            originatingFile = function.containingFile
        )
    }

    private fun parseConcurrency(raw: Any?, serialQueueSize: Int, overflow: QueueOverflowPolicy): ConcurrencyMeta {
        val name = extractEnumName(raw) ?: "SKIP_IF_RUNNING"
        return when (name) {
            "ALLOW" -> ConcurrencyMeta.Allow
            "QUEUE_ONE" -> ConcurrencyMeta.QueueOne
            "SERIAL" -> ConcurrencyMeta.Serial(serialQueueSize, overflow)
            else -> ConcurrencyMeta.SkipIfRunning
        }
    }

    private fun parseMisfire(raw: Any?, catchUpLimit: Int): MisfireMeta {
        val name = extractEnumName(raw) ?: "SKIP"
        return when (name) {
            "FIRE_ONCE" -> MisfireMeta.FireOnce
            "CATCH_UP" -> MisfireMeta.CatchUp(catchUpLimit)
            else -> MisfireMeta.Skip
        }
    }

    private fun parseSerialOverflow(raw: Any?): QueueOverflowPolicy {
        val name = extractEnumName(raw) ?: "DROP_OLDEST"
        return when (name) {
            "DROP_LATEST" -> QueueOverflowPolicy.DROP_LATEST
            "REJECT" -> QueueOverflowPolicy.REJECT
            else -> QueueOverflowPolicy.DROP_OLDEST
        }
    }

    private fun extractEnumName(value: Any?): String? {
        if (value == null) return null
        if (value is String) return value
        if (value is KSType) return value.declaration.simpleName.asString()
        return value.toString().substringAfterLast('.')
    }
}

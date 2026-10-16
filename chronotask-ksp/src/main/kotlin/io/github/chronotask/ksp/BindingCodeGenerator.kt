package io.github.chronotask.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeName
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeVariableName
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver
import io.github.chronotask.cron.CronDialect
import io.github.chronotask.engine.ChronoTaskEngine
import io.github.chronotask.engine.TaskHandle
import io.github.chronotask.job.TaskExecutable
import io.github.chronotask.model.ConcurrencyPolicy
import io.github.chronotask.model.MisfirePolicy
import io.github.chronotask.model.QueueOverflowPolicy
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.time.ZoneId

internal class BindingCodeGenerator(
    private val codeGenerator: CodeGenerator
) {

    fun generate(target: TargetClassMeta) {
        val targetClassName = target.ksClass.toClassName()
        val isInner = Modifier.INNER in target.ksClass.modifiers
        val typeVariables = if (isInner) emptyList() else {
            val resolver = target.typeParameters.toTypeParameterResolver()
            target.typeParameters.map {
                val variable = it.toTypeVariableName(resolver)
                // Class declaration variance is invalid on function type parameters.
                TypeVariableName(variable.name, bounds = variable.bounds)
            }
        }

        val targetType = if (isInner) {
            projectedInnerType(target.ksClass)
        } else if (typeVariables.isNotEmpty()) {
            targetClassName.parameterizedBy(typeVariables)
        } else {
            targetClassName
        }

        val definitionsFunBuilder = FunSpec.builder("definitions")
            .returns(List::class.asClassName().parameterizedBy(TaskDefinition::class.asClassName()))
            .addParameter("target", targetType)

        if (typeVariables.isNotEmpty()) {
            definitionsFunBuilder.addTypeVariables(typeVariables)
        }

        val taskDefBlocks = mutableListOf<CodeBlock>()
        for (task in target.tasks) {
            taskDefBlocks.add(generateTaskDefinitionCode(task))
        }

        val definitionsBody = CodeBlock.builder()
            .add("return listOf(\n")
            .indent()

        taskDefBlocks.forEachIndexed { index, block ->
            definitionsBody.add(block)
            if (index < taskDefBlocks.size - 1) {
                definitionsBody.add(",\n")
            } else {
                definitionsBody.add("\n")
            }
        }

        definitionsBody.unindent().add(")")
        definitionsFunBuilder.addCode(definitionsBody.build())

        val registerFunBuilder = FunSpec.builder("register")
            .returns(List::class.asClassName().parameterizedBy(TaskHandle::class.asClassName()))
            .addParameter("engine", ChronoTaskEngine::class.asClassName())
            .addParameter("target", targetType)

        if (typeVariables.isNotEmpty()) {
            registerFunBuilder.addTypeVariables(typeVariables)
        }

        val registerBody = CodeBlock.builder()
            .addStatement("val defs = definitions(target)")
            .addStatement("val registeredHandles = %T<%T>(defs.size)", ArrayList::class.asClassName(), TaskHandle::class.asClassName())
            .beginControlFlow("try")
            .beginControlFlow("for (def in defs)")
            .addStatement("registeredHandles.add(engine.register(def))")
            .endControlFlow()
            .addStatement("return registeredHandles")
            .nextControlFlow("catch (t: %T)", Throwable::class.asClassName())
            .beginControlFlow("for (handle in registeredHandles.asReversed())")
            .beginControlFlow("try")
            .addStatement("handle.unregister()")
            .nextControlFlow("catch (rollbackEx: %T)", Throwable::class.asClassName())
            .addStatement("t.addSuppressed(rollbackEx)")
            .endControlFlow()
            .endControlFlow()
            .addStatement("throw t")
            .endControlFlow()

        registerFunBuilder.addCode(registerBody.build())

        val bindingObject = TypeSpec.objectBuilder(target.bindingSimpleName)
            .apply { if (target.isInternal) addModifiers(KModifier.INTERNAL) }
            .addFunction(definitionsFunBuilder.build())
            .addFunction(registerFunBuilder.build())
            .build()

        val fileBuilder = FileSpec.builder(target.packageName, target.bindingSimpleName)
            .addType(bindingObject)
        // Function type parameters shadow imported classes in signatures and bodies.
        // Alias any referenced class with a conflicting name, including bound types.
        val parameterNames = typeVariables.map { it.name }.toSet()
        val referencedClasses = linkedSetOf<ClassName>()
        fun collectClasses(type: TypeName) {
            when (type) {
                is ClassName -> referencedClasses.add(ClassName(type.packageName, type.simpleNames))
                is ParameterizedTypeName -> {
                    collectClasses(type.rawType)
                    type.typeArguments.forEach(::collectClasses)
                }
                is WildcardTypeName -> (type.inTypes + type.outTypes).forEach(::collectClasses)
                is TypeVariableName -> type.bounds.forEach(::collectClasses)
                is LambdaTypeName -> {
                    type.receiver?.let(::collectClasses)
                    collectClasses(type.returnType)
                    type.parameters.forEach { collectClasses(it.type) }
                }
                else -> Unit
            }
        }
        collectClasses(targetType)
        listOf(
            List::class, ArrayList::class, Throwable::class, TaskDefinition::class,
            TaskHandle::class, ChronoTaskEngine::class, TaskSchedule::class,
            TaskExecutable::class, ConcurrencyPolicy::class, MisfirePolicy::class,
            QueueOverflowPolicy::class, ZoneId::class, CronDialect::class
        ).forEach { collectClasses(it.asClassName()) }
        typeVariables.forEach(::collectClasses)
        val occupiedNames = (parameterNames + referencedClasses.flatMap { it.simpleNames } +
            target.bindingSimpleName).toMutableSet()
        for (className in referencedClasses) {
            if (className.simpleNames.none { it in parameterNames }) continue
            var alias = "ChronoTask${className.simpleName}"
            while (!occupiedNames.add(alias)) alias += "_"
            fileBuilder.addAliasedImport(className, alias)
        }
        val fileSpec = fileBuilder.build()

        val dependencies = Dependencies(
            aggregating = false,
            *target.originatingFiles.toTypedArray()
        )

        val outputStream = codeGenerator.createNewFile(
            dependencies = dependencies,
            packageName = target.packageName,
            fileName = target.bindingSimpleName
        )

        OutputStreamWriter(outputStream, StandardCharsets.UTF_8).use { writer ->
            fileSpec.writeTo(writer)
        }
    }

    // Task calls use only Unit and TaskExecutionContext, so projections retain safe
    // access without exposing captured outer type variables in the binding API.
    private fun projectedInnerType(declaration: KSClassDeclaration): TypeName {
        val arguments = declaration.typeParameters.map { STAR }
        val enclosing = if (Modifier.INNER in declaration.modifiers)
            declaration.parentDeclaration as? KSClassDeclaration else null
        if (enclosing != null) {
            val outerType = projectedInnerType(enclosing)
            val name = declaration.simpleName.asString()
            if (outerType is ParameterizedTypeName) {
                return outerType.nestedClass(name, arguments)
            }
            val nested = (outerType as ClassName).nestedClass(name)
            return if (arguments.isEmpty()) nested else nested.parameterizedBy(arguments)
        }
        val className = declaration.toClassName()
        return if (arguments.isEmpty()) className else className.parameterizedBy(arguments)
    }

    private fun generateTaskDefinitionCode(task: TaskMethodMeta): CodeBlock {
        val builder = CodeBlock.builder()
        builder.add("%T(\n", TaskDefinition::class.asClassName())
        builder.indent()

        // 1. id
        builder.add("id = %S,\n", task.taskId)

        // 2. name
        builder.add("name = %S,\n", task.taskName)

        // 3. schedule
        when (val s = task.schedule) {
            is TaskScheduleMeta.FixedRate -> {
                builder.add(
                    "schedule = %T.FixedRate(intervalMs = %LL, initialDelayMs = %LL),\n",
                    TaskSchedule::class.asClassName(),
                    s.intervalMs,
                    s.initialDelayMs
                )
            }
            is TaskScheduleMeta.FixedDelay -> {
                builder.add(
                    "schedule = %T.FixedDelay(delayMs = %LL, initialDelayMs = %LL),\n",
                    TaskSchedule::class.asClassName(),
                    s.intervalMs,
                    s.initialDelayMs
                )
            }
            is TaskScheduleMeta.Cron -> {
                builder.add(
                    "schedule = %T.Cron(expression = %S, zoneId = %T.of(%S), dialect = %T.%L),\n",
                    TaskSchedule::class.asClassName(),
                    s.expression,
                    ZoneId::class.asClassName(),
                    s.zone,
                    CronDialect::class.asClassName(),
                    s.dialect.name
                )
            }
            is TaskScheduleMeta.Once -> {
                builder.add(
                    "schedule = %T.Once(delayMs = %LL),\n",
                    TaskSchedule::class.asClassName(),
                    s.delayMs
                )
            }
        }

        // 4. concurrencyPolicy
        when (val c = task.concurrency) {
            is ConcurrencyMeta.Allow -> {
                builder.add("concurrencyPolicy = %T.Allow,\n", ConcurrencyPolicy::class.asClassName())
            }
            is ConcurrencyMeta.SkipIfRunning -> {
                builder.add("concurrencyPolicy = %T.SkipIfRunning,\n", ConcurrencyPolicy::class.asClassName())
            }
            is ConcurrencyMeta.QueueOne -> {
                builder.add("concurrencyPolicy = %T.QueueOne,\n", ConcurrencyPolicy::class.asClassName())
            }
            is ConcurrencyMeta.Serial -> {
                builder.add(
                    "concurrencyPolicy = %T.Serial(maxQueueSize = %L, overflowPolicy = %T.%L),\n",
                    ConcurrencyPolicy::class.asClassName(),
                    c.queueSize,
                    QueueOverflowPolicy::class.asClassName(),
                    c.overflow.name
                )
            }
        }

        // 5. misfirePolicy
        when (val m = task.misfire) {
            is MisfireMeta.Skip -> {
                builder.add("misfirePolicy = %T.SKIP,\n", MisfirePolicy::class.asClassName())
            }
            is MisfireMeta.FireOnce -> {
                builder.add("misfirePolicy = %T.FIRE_ONCE,\n", MisfirePolicy::class.asClassName())
            }
            is MisfireMeta.CatchUp -> {
                builder.add("misfirePolicy = %T.CatchUp(maxExecutions = %L),\n", MisfirePolicy::class.asClassName(), m.catchUpLimit)
            }
        }

        // 6. dispatcherKey
        if (task.dispatcherKey != null) {
            builder.add("dispatcherKey = %S,\n", task.dispatcherKey)
        } else {
            builder.add("dispatcherKey = null,\n")
        }

        // 7. isEnabled
        builder.add("isEnabled = %L,\n", task.isEnabled)

        // 8. executable
        if (task.hasContextParam) {
            builder.add("executable = %T { ctx -> target.%N(ctx) }\n", TaskExecutable::class.asClassName(), task.methodName)
        } else {
            builder.add("executable = %T { target.%N() }\n", TaskExecutable::class.asClassName(), task.methodName)
        }

        builder.unindent()
        builder.add(")")
        return builder.build()
    }
}

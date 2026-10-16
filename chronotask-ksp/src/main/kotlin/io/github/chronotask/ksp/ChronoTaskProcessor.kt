package io.github.chronotask.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo

class ChronoTaskProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val annotations = listOf(
            "io.github.chronotask.annotation.TickTask",
            "io.github.chronotask.annotation.Cron",
            "io.github.chronotask.annotation.Once",
            "io.github.chronotask.annotation.FixedRate",
            "io.github.chronotask.annotation.FixedDelay",
            "io.github.chronotask.annotation.Scheduled"
        )

        val allFunctions = annotations.flatMap { ann ->
            resolver.getSymbolsWithAnnotation(ann).filterIsInstance<KSFunctionDeclaration>()
        }.distinct()

        if (!allFunctions.iterator().hasNext()) return emptyList()

        val groupedByClass = allFunctions.groupBy { it.parentDeclaration as? KSClassDeclaration }

        for ((classDecl, functions) in groupedByClass) {
            if (classDecl == null) {
                logger.warn("ChronoTask KSP: Top-level functions with scheduling annotations are not supported yet.")
                continue
            }
            generateClassRegistrar(classDecl, functions)
        }

        return emptyList()
    }

    private fun generateClassRegistrar(
        classDecl: KSClassDeclaration,
        functions: List<KSFunctionDeclaration>
    ) {
        val packageName = classDecl.packageName.asString()
        val className = classDecl.simpleName.asString()
        val registrarClassName = "${className}_ChronoTaskRegistrar"
        val targetClass = classDecl.toClassName()

        val taskDefinitionClass = ClassName("io.github.chronotask.model", "TaskDefinition")
        val engineClass = ClassName("io.github.chronotask.engine", "ChronoTaskEngine")
        val taskHandleClass = ClassName("io.github.chronotask.engine", "TaskHandle")
        val taskScheduleClass = ClassName("io.github.chronotask.model", "TaskSchedule")
        val concurrencyPolicyClass = ClassName("io.github.chronotask.model", "ConcurrencyPolicy")
        val misfirePolicyClass = ClassName("io.github.chronotask.model", "MisfirePolicy")
        val timeUnitClass = ClassName("java.util.concurrent", "TimeUnit")
        val registrarInterface = ClassName("io.github.chronotask.spi", "ChronoTaskRegistrar")

        val getTasksFun = FunSpec.builder("getTasks")
            .addParameter("target", targetClass)
            .returns(List::class.asClassName().parameterizedBy(taskDefinitionClass))
            .addCode("val tasks = mutableListOf<%T>()\n", taskDefinitionClass)

        for (func in functions) {
            val funcName = func.simpleName.asString()
            val hasContextParam = func.parameters.size == 1 &&
                    func.parameters[0].type.resolve().declaration.qualifiedName?.asString() == "io.github.chronotask.model.TaskExecutionContext"

            val tickAnn = func.annotations.firstOrNull { it.shortName.asString() == "TickTask" }
            val cronAnn = func.annotations.firstOrNull { it.shortName.asString() == "Cron" }
            val onceAnn = func.annotations.firstOrNull { it.shortName.asString() == "Once" }
            val fixedRateAnn = func.annotations.firstOrNull { it.shortName.asString() == "FixedRate" }
            val fixedDelayAnn = func.annotations.firstOrNull { it.shortName.asString() == "FixedDelay" }
            val scheduledAnn = func.annotations.firstOrNull { it.shortName.asString() == "Scheduled" }

            val (scheduleCode, jobName, concurrencyCode, misfireCode) = when {
                tickAnn != null -> {
                    val interval = tickAnn.arguments.firstOrNull { it.name?.asString() == "interval" }?.value as? Long ?: 1L
                    val name = (tickAnn.arguments.firstOrNull { it.name?.asString() == "name" }?.value as? String)?.takeIf { it.isNotBlank() }
                        ?: "$className.$funcName"
                    Quad(CodeBlock.of("%T.FixedRate(%T.SECONDS.toMillis(%LL), 0L)", taskScheduleClass, timeUnitClass, interval), name, CodeBlock.of("%T.SkipIfRunning", concurrencyPolicyClass), CodeBlock.of("%T.Skip", misfirePolicyClass))
                }
                cronAnn != null -> {
                    val cronVal = cronAnn.arguments.firstOrNull { it.name?.asString() == "value" }?.value as? String ?: ""
                    val name = (cronAnn.arguments.firstOrNull { it.name?.asString() == "name" }?.value as? String)?.takeIf { it.isNotBlank() }
                        ?: "$className.$funcName"
                    Quad(CodeBlock.of("%T.Cron(%S)", taskScheduleClass, cronVal), name, CodeBlock.of("%T.SkipIfRunning", concurrencyPolicyClass), CodeBlock.of("%T.Skip", misfirePolicyClass))
                }
                onceAnn != null -> {
                    val delay = onceAnn.arguments.firstOrNull { it.name?.asString() == "delay" }?.value as? Long ?: 0L
                    val name = (onceAnn.arguments.firstOrNull { it.name?.asString() == "name" }?.value as? String)?.takeIf { it.isNotBlank() }
                        ?: "$className.$funcName"
                    Quad(CodeBlock.of("%T.Once(%LL)", taskScheduleClass, delay), name, CodeBlock.of("%T.SkipIfRunning", concurrencyPolicyClass), CodeBlock.of("%T.Skip", misfirePolicyClass))
                }
                fixedRateAnn != null -> {
                    val interval = fixedRateAnn.arguments.firstOrNull { it.name?.asString() == "interval" }?.value as? Long ?: 1000L
                    val name = (fixedRateAnn.arguments.firstOrNull { it.name?.asString() == "name" }?.value as? String)?.takeIf { it.isNotBlank() }
                        ?: "$className.$funcName"
                    Quad(CodeBlock.of("%T.FixedRate(%LL, 0L)", taskScheduleClass, interval), name, CodeBlock.of("%T.SkipIfRunning", concurrencyPolicyClass), CodeBlock.of("%T.Skip", misfirePolicyClass))
                }
                fixedDelayAnn != null -> {
                    val delay = fixedDelayAnn.arguments.firstOrNull { it.name?.asString() == "delay" }?.value as? Long ?: 1000L
                    val name = (fixedDelayAnn.arguments.firstOrNull { it.name?.asString() == "name" }?.value as? String)?.takeIf { it.isNotBlank() }
                        ?: "$className.$funcName"
                    Quad(CodeBlock.of("%T.FixedDelay(%LL, 0L)", taskScheduleClass, delay), name, CodeBlock.of("%T.SkipIfRunning", concurrencyPolicyClass), CodeBlock.of("%T.Skip", misfirePolicyClass))
                }
                scheduledAnn != null -> {
                    val fixedRate = scheduledAnn.arguments.firstOrNull { it.name?.asString() == "fixedRate" }?.value as? Long ?: -1L
                    val fixedDelay = scheduledAnn.arguments.firstOrNull { it.name?.asString() == "fixedDelay" }?.value as? Long ?: -1L
                    val cron = scheduledAnn.arguments.firstOrNull { it.name?.asString() == "cron" }?.value as? String ?: ""
                    val name = (scheduledAnn.arguments.firstOrNull { it.name?.asString() == "name" }?.value as? String)?.takeIf { it.isNotBlank() }
                        ?: "$className.$funcName"

                    val scheduleBlock = when {
                        cron.isNotBlank() -> CodeBlock.of("%T.Cron(%S)", taskScheduleClass, cron)
                        fixedRate > 0 -> CodeBlock.of("%T.FixedRate(%LL, 0L)", taskScheduleClass, fixedRate)
                        fixedDelay > 0 -> CodeBlock.of("%T.FixedDelay(%LL, 0L)", taskScheduleClass, fixedDelay)
                        else -> CodeBlock.of("%T.FixedRate(1000L, 0L)", taskScheduleClass)
                    }
                    Quad(scheduleBlock, name, CodeBlock.of("%T.SkipIfRunning", concurrencyPolicyClass), CodeBlock.of("%T.Skip", misfirePolicyClass))
                }
                else -> continue
            }

            val invocationCode = if (hasContextParam) {
                "target.$funcName(context)"
            } else {
                "target.$funcName()"
            }

            getTasksFun.addCode(
                """
                tasks.add(
                    %T(
                        id = %S,
                        name = %S,
                        schedule = %L,
                        concurrencyPolicy = %L,
                        misfirePolicy = %L,
                        executable = { context ->
                            $invocationCode
                        }
                    )
                )
                """.trimIndent() + "\n",
                taskDefinitionClass,
                jobName,
                jobName,
                scheduleCode,
                concurrencyCode,
                misfireCode
            )
        }

        getTasksFun.addCode("return tasks\n")

        val registerTypedFun = FunSpec.builder("register")
            .addParameter("engine", engineClass)
            .addParameter("target", targetClass)
            .returns(List::class.asClassName().parameterizedBy(taskHandleClass))
            .addCode("return engine.registerAllDefinitions(getTasks(target))\n")

        val registerSpiFun = FunSpec.builder("register")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("engine", engineClass)
            .addParameter("target", Any::class)
            .returns(List::class.asClassName().parameterizedBy(taskHandleClass))
            .addCode("return register(engine, target as %T)\n", targetClass)

        val registrarObject = TypeSpec.objectBuilder(registrarClassName)
            .addSuperinterface(registrarInterface)
            .addFunction(getTasksFun.build())
            .addFunction(registerTypedFun.build())
            .addFunction(registerSpiFun.build())
            .build()

        val extensionFun = FunSpec.builder("registerAnnotatedGenerated")
            .receiver(engineClass)
            .addParameter("target", targetClass)
            .returns(List::class.asClassName().parameterizedBy(taskHandleClass))
            .addCode("return %T.register(this, target)\n", ClassName(packageName, registrarClassName))
            .build()

        val fileSpec = FileSpec.builder(packageName, registrarClassName)
            .addType(registrarObject)
            .addFunction(extensionFun)
            .build()

        fileSpec.writeTo(
            codeGenerator = codeGenerator,
            dependencies = Dependencies(false, classDecl.containingFile!!)
        )
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}

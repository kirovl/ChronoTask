package io.github.chronotask.processor

import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import com.squareup.javapoet.TypeVariableName
import com.squareup.javapoet.WildcardTypeName
import io.github.chronotask.cron.CronDialect
import io.github.chronotask.engine.ChronoTaskEngine
import io.github.chronotask.engine.TaskHandle
import io.github.chronotask.job.TaskExecutable
import io.github.chronotask.model.ConcurrencyPolicy
import io.github.chronotask.model.MisfirePolicy
import io.github.chronotask.model.QueueOverflowPolicy
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.model.TaskSchedule
import java.time.ZoneId
import java.util.ArrayList
import java.util.Collections
import java.util.Objects
import javax.annotation.processing.Filer
import javax.annotation.processing.Generated
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement

internal class BindingCodeGenerator(
    private val filer: Filer
) {

    fun generate(target: TargetClassMeta) {
        val isInner = target.typeElement.enclosingElement is TypeElement &&
            Modifier.STATIC !in target.typeElement.modifiers
        val targetTypeName = if (isInner) projectedInnerType(target.typeElement)
            else TypeName.get(target.typeElement.asType())
        val typeVariables = if (isInner) emptyList() else target.typeParameters.map { TypeVariableName.get(it) }

        // definitions method
        val definitionsBuilder = MethodSpec.methodBuilder("definitions")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(ParameterizedTypeName.get(ClassName.get(List::class.java), ClassName.get(TaskDefinition::class.java)))
            .addParameter(targetTypeName, "target")

        for (tv in typeVariables) {
            definitionsBuilder.addTypeVariable(tv)
        }

        definitionsBuilder.addStatement("\$T.requireNonNull(target, \$S)", Objects::class.java, "target must not be null")
        definitionsBuilder.addStatement(
            "\$T<\$T> defs = new \$T<>(\$L)",
            List::class.java,
            TaskDefinition::class.java,
            ArrayList::class.java,
            target.tasks.size
        )

        for (task in target.tasks) {
            definitionsBuilder.addStatement("defs.add(\$L)", generateTaskDefinitionCode(task))
        }

        definitionsBuilder.addStatement("return \$T.unmodifiableList(defs)", Collections::class.java)

        // register method
        val registerBuilder = MethodSpec.methodBuilder("register")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(ParameterizedTypeName.get(ClassName.get(List::class.java), ClassName.get(TaskHandle::class.java)))
            .addParameter(ClassName.get(ChronoTaskEngine::class.java), "engine")
            .addParameter(targetTypeName, "target")

        for (tv in typeVariables) {
            registerBuilder.addTypeVariable(tv)
        }

        registerBuilder.addStatement("\$T.requireNonNull(engine, \$S)", Objects::class.java, "engine must not be null")
        registerBuilder.addStatement("\$T.requireNonNull(target, \$S)", Objects::class.java, "target must not be null")
        registerBuilder.addStatement("\$T<\$T> defs = definitions(target)", List::class.java, TaskDefinition::class.java)
        registerBuilder.addStatement(
            "\$T<\$T> registeredHandles = new \$T<>(defs.size())",
            List::class.java,
            TaskHandle::class.java,
            ArrayList::class.java
        )

        registerBuilder.beginControlFlow("try")
        registerBuilder.beginControlFlow("for (\$T def : defs)", TaskDefinition::class.java)
        registerBuilder.addStatement("registeredHandles.add(engine.register(def))")
        registerBuilder.endControlFlow()
        registerBuilder.addStatement("return registeredHandles")
        registerBuilder.nextControlFlow("catch (\$T t)", Throwable::class.java)
        registerBuilder.beginControlFlow("for (int i = registeredHandles.size() - 1; i >= 0; i--)")
        registerBuilder.addStatement("\$T handle = registeredHandles.get(i)", TaskHandle::class.java)
        registerBuilder.beginControlFlow("try")
        registerBuilder.addStatement("handle.unregister()")
        registerBuilder.nextControlFlow("catch (\$T rollbackEx)", Throwable::class.java)
        registerBuilder.beginControlFlow("if (rollbackEx != t)")
        registerBuilder.addStatement("t.addSuppressed(rollbackEx)")
        registerBuilder.endControlFlow()
        registerBuilder.endControlFlow()
        registerBuilder.endControlFlow()
        registerBuilder.addStatement("throw t")
        registerBuilder.endControlFlow()

        // generated class
        val classBuilder = TypeSpec.classBuilder(target.bindingSimpleName)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(
                AnnotationSpec.builder(Generated::class.java)
                    .addMember("value", "\$S", "io.github.chronotask.processor.ChronoTaskProcessor")
                    .build()
            )
            .addMethod(
                MethodSpec.constructorBuilder()
                    .addModifiers(Modifier.PRIVATE)
                    .build()
            )
            .addMethod(definitionsBuilder.build())
            .addMethod(registerBuilder.build())

        val javaFile = JavaFile.builder(target.packageName, classBuilder.build())
            .indent("    ")
            .skipJavaLangImports(true)
            .build()

        val originatingArray = target.originatingElements.toTypedArray()
        val fqcn = if (target.packageName.isEmpty()) target.bindingSimpleName else "${target.packageName}.${target.bindingSimpleName}"
        val sourceFile = filer.createSourceFile(fqcn, *originatingArray)
        sourceFile.openWriter().use { writer ->
            javaFile.writeTo(writer)
        }
    }

    // Task signatures do not expose captured generic types, so wildcard targets
    // avoid leaking outer type variables into static binding methods.
    private fun projectedInnerType(type: TypeElement): TypeName {
        val arguments: List<TypeName> = type.typeParameters.map { WildcardTypeName.subtypeOf(Any::class.java) }
        val outer = (type.enclosingElement as? TypeElement)?.takeIf { Modifier.STATIC !in type.modifiers }
        if (outer != null) {
            val outerType = projectedInnerType(outer)
            val name = type.simpleName.toString()
            if (outerType is ParameterizedTypeName) {
                return outerType.nestedClass(name, arguments)
            }
            val nested = (outerType as ClassName).nestedClass(name)
            return if (arguments.isEmpty()) nested else ParameterizedTypeName.get(nested, *arguments.toTypedArray())
        }
        val className = ClassName.get(type)
        return if (arguments.isEmpty()) className else ParameterizedTypeName.get(className, *arguments.toTypedArray())
    }

    private fun generateTaskDefinitionCode(task: TaskMethodMeta): CodeBlock {
        val builder = CodeBlock.builder()
        builder.add("new \$T(\n", TaskDefinition::class.java)
        builder.indent()

        // 1. id
        builder.add("\$S,\n", task.taskId)

        // 2. name
        builder.add("\$S,\n", task.taskName)

        // 3. schedule
        when (val s = task.schedule) {
            is TaskScheduleMeta.FixedRate -> {
                builder.add(
                    "new \$T.FixedRate(\$LL, \$LL),\n",
                    TaskSchedule::class.java,
                    s.intervalMs,
                    s.initialDelayMs
                )
            }
            is TaskScheduleMeta.FixedDelay -> {
                builder.add(
                    "new \$T.FixedDelay(\$LL, \$LL),\n",
                    TaskSchedule::class.java,
                    s.intervalMs,
                    s.initialDelayMs
                )
            }
            is TaskScheduleMeta.Cron -> {
                builder.add(
                    "new \$T.Cron(\$S, \$T.of(\$S), \$T.\$L),\n",
                    TaskSchedule::class.java,
                    s.expression,
                    ZoneId::class.java,
                    s.zone,
                    CronDialect::class.java,
                    s.dialect.name
                )
            }
            is TaskScheduleMeta.Once -> {
                builder.add(
                    "new \$T.Once(\$LL),\n",
                    TaskSchedule::class.java,
                    s.delayMs
                )
            }
        }

        // 4. concurrencyPolicy
        when (val c = task.concurrency) {
            is ConcurrencyMeta.Allow -> {
                builder.add("\$T.ALLOW,\n", ConcurrencyPolicy::class.java)
            }
            is ConcurrencyMeta.SkipIfRunning -> {
                builder.add("\$T.SKIP_IF_RUNNING,\n", ConcurrencyPolicy::class.java)
            }
            is ConcurrencyMeta.QueueOne -> {
                builder.add("\$T.QUEUE_ONE,\n", ConcurrencyPolicy::class.java)
            }
            is ConcurrencyMeta.Serial -> {
                builder.add(
                    "new \$T.Serial(\$L, \$T.\$L),\n",
                    ConcurrencyPolicy::class.java,
                    c.queueSize,
                    QueueOverflowPolicy::class.java,
                    c.overflow.name
                )
            }
        }

        // 5. misfirePolicy
        when (val m = task.misfire) {
            is MisfireMeta.Skip -> {
                builder.add("\$T.SKIP,\n", MisfirePolicy::class.java)
            }
            is MisfireMeta.FireOnce -> {
                builder.add("\$T.FIRE_ONCE,\n", MisfirePolicy::class.java)
            }
            is MisfireMeta.CatchUp -> {
                builder.add(
                    "new \$T.CatchUp(\$L),\n",
                    MisfirePolicy::class.java,
                    m.catchUpLimit
                )
            }
        }

        // 6. dispatcherKey
        if (task.dispatcherKey != null) {
            builder.add("\$S,\n", task.dispatcherKey)
        } else {
            builder.add("null,\n")
        }

        // 7. isEnabled
        builder.add("\$L,\n", task.isEnabled)

        // 8. executable
        if (task.hasContextParam) {
            builder.add("\$T.fromJava(context -> target.\$L(context))\n", TaskExecutable::class.java, task.methodName)
        } else {
            builder.add("\$T.fromJava(context -> target.\$L())\n", TaskExecutable::class.java, task.methodName)
        }

        builder.unindent()
        builder.add(")")
        return builder.build()
    }
}

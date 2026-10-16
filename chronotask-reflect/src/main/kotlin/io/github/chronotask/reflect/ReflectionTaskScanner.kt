package io.github.chronotask.reflect

import io.github.chronotask.annotation.Cron
import io.github.chronotask.annotation.Once
import io.github.chronotask.annotation.Scheduled
import io.github.chronotask.annotation.TickTask
import io.github.chronotask.exception.TaskConfigurationException
import io.github.chronotask.job.TaskExecutionContext
import io.github.chronotask.model.TaskDefinition
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.coroutines.Continuation
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.extensionReceiverParameter
import kotlin.reflect.jvm.kotlinFunction

/**
 * Scans an object instance to discover and extract annotated methods into [TaskDefinition] instances.
 */
internal object ReflectionTaskScanner {

    private data class MethodSignature(
        val name: String,
        val paramTypes: List<Class<*>>
    )

    private data class ScannedMethod(
        val method: Method,
        val definition: TaskDefinition
    )

    /**
     * Scans [instance] across its class hierarchy and returns a deterministically ordered list of [TaskDefinition]s.
     */
    fun scan(instance: Any): List<TaskDefinition> {
        val scanned = mutableListOf<ScannedMethod>()
        val seenSignatures = mutableSetOf<MethodSignature>()
        val seenTaskIds = mutableSetOf<String>()

        var currentClass: Class<*>? = instance.javaClass
        while (currentClass != null && currentClass != Any::class.java && currentClass != java.lang.Object::class.java) {
            // Get declared methods in current hierarchy level
            val declaredMethods = currentClass.declaredMethods
            val kotlinFunctions = declaredMethods.associateWith { runCatching { it.kotlinFunction }.getOrNull() }
            val generatedOverloads = generatedDefaultOverloadSignatures(kotlinFunctions)

            for (method in declaredMethods) {
                if (method.isBridge || method.isSynthetic) {
                    continue
                }

                val signature = MethodSignature(
                    name = method.name,
                    paramTypes = method.parameterTypes.toList()
                )

                val kotlinFunction = kotlinFunctions[method]
                // @JvmOverloads copies annotations onto JVM methods absent from Kotlin
                // metadata. Only the source method should contribute a task or an override.
                if (kotlinFunction == null && signature in generatedOverloads) {
                    continue
                }

                if (signature in seenSignatures) {
                    // Method already declared/overridden in a subclass
                    // If subclass did not annotate it, base annotation is suppressed.
                    // If subclass annotated it, it was already scanned in the subclass.
                    continue
                }
                seenSignatures.add(signature)

                val scheduledAnn = method.getAnnotation(Scheduled::class.java)
                val cronAnn = method.getAnnotation(Cron::class.java)
                val onceAnn = method.getAnnotation(Once::class.java)
                val tickAnn = method.getAnnotation(TickTask::class.java)

                val annotationsFound = listOfNotNull(scheduledAnn, cronAnn, onceAnn, tickAnn)
                if (annotationsFound.isEmpty()) {
                    continue
                }

                val methodRef = "${currentClass.name}#${method.name}"

                // 1. Exactly one scheduling annotation constraint
                if (annotationsFound.size > 1) {
                    val annotationNames = annotationsFound.joinToString { it.annotationClass.simpleName ?: "" }
                    throw TaskConfigurationException(
                        "Method '$methodRef' has multiple scheduling annotations ($annotationNames). Exactly one scheduling annotation is allowed per method."
                    )
                }

                // 2. Visibility constraint: Reject private and static
                if (Modifier.isPrivate(method.modifiers)) {
                    throw TaskConfigurationException("Annotated method '$methodRef' must not be private.")
                }
                if (Modifier.isStatic(method.modifiers)) {
                    throw TaskConfigurationException("Annotated method '$methodRef' must not be static.")
                }

                // 3. Signature & Return Type constraint
                validateMethodSignature(method, methodRef, kotlinFunction)

                // 4. Default ID generation
                // Match source names used by KSP/APT, rather than JVM nesting or mangling.
                val className = currentClass.canonicalName ?: currentClass.name
                val functionName = kotlinFunction?.name ?: method.name
                val defaultId = "$className#$functionName"
                val explicitId = when {
                    scheduledAnn != null -> scheduledAnn.id
                    cronAnn != null -> cronAnn.id
                    onceAnn != null -> onceAnn.id
                    tickAnn != null -> tickAnn.id
                    else -> ""
                }.trim()
                val resolvedId = explicitId.ifBlank { defaultId }

                // 5. Duplicate ID detection in current scan batch
                if (resolvedId in seenTaskIds) {
                    throw TaskConfigurationException(
                        "Duplicate task ID '$resolvedId' detected during scanning of ${instance.javaClass.name} on method '$methodRef'"
                    )
                }
                seenTaskIds.add(resolvedId)

                // 6. Build TaskDefinition
                val adapter = ReflectionMethodAdapter(instance, method)
                val taskDefinition = when {
                    scheduledAnn != null -> AnnotationTaskDefinitionFactory.createFromScheduled(scheduledAnn, resolvedId, adapter)
                    cronAnn != null -> AnnotationTaskDefinitionFactory.createFromCron(cronAnn, resolvedId, adapter)
                    onceAnn != null -> AnnotationTaskDefinitionFactory.createFromOnce(onceAnn, resolvedId, adapter)
                    tickAnn != null -> AnnotationTaskDefinitionFactory.createFromTickTask(tickAnn, resolvedId, adapter)
                    else -> error("Unexpected state: no annotation present")
                }

                scanned.add(ScannedMethod(method, taskDefinition))
            }

            currentClass = currentClass.superclass
        }

        // 7. Deterministic sorting by declaring class name, method name, method descriptor
        return scanned.sortedWith(
            compareBy<ScannedMethod> { it.method.declaringClass.name }
                .thenBy { it.method.name }
                .thenBy { it.method.toGenericString() }
        ).map { it.definition }
    }

    private fun generatedDefaultOverloadSignatures(
        kotlinFunctions: Map<Method, KFunction<*>?>
    ): Set<MethodSignature> {
        val signatures = mutableSetOf<MethodSignature>()
        for ((method, function) in kotlinFunctions) {
            if (function == null) continue
            val values = function.parameters.filter { it.kind == KParameter.Kind.VALUE }
            val receiverCount = if (function.extensionReceiverParameter != null) 1 else 0
            val continuationCount = if (function.isSuspend) 1 else 0
            // Avoid guessing for JVM signatures transformed by other compiler features.
            if (method.parameterCount != receiverCount + values.size + continuationCount) continue
            val parameterTypes = method.parameterTypes.toMutableList()
            // JVM overloads omit optional value parameters from right to left, retaining
            // required parameters and the final Continuation for suspend functions.
            for (index in values.indices.reversed()) {
                if (values[index].isOptional) {
                    parameterTypes.removeAt(receiverCount + index)
                    signatures.add(MethodSignature(method.name, parameterTypes.toList()))
                }
            }
        }
        return signatures
    }

    private fun validateMethodSignature(method: Method, methodRef: String, kotlinFunction: KFunction<*>?) {
        val paramTypes = method.parameterTypes
        if (kotlinFunction?.isSuspend == true) {
            val valid = (paramTypes.size == 1 && Continuation::class.java.isAssignableFrom(paramTypes[0])) ||
                    (paramTypes.size == 2 && TaskExecutionContext::class.java.isAssignableFrom(paramTypes[0]) &&
                            Continuation::class.java.isAssignableFrom(paramTypes[1]))
            if (!valid) {
                val paramNames = paramTypes.joinToString { it.simpleName }
                throw TaskConfigurationException(
                    "Suspend scheduled method '$methodRef' has invalid parameters ($paramNames). Expected () or (TaskExecutionContext)."
                )
            }
            // Suspend methods return Object at the JVM level; inspect the source return type.
            val returnType = kotlinFunction.returnType
            if (returnType.classifier != Unit::class) {
                throw TaskConfigurationException(
                    "Suspend scheduled method '$methodRef' must have Unit return type, but returns $returnType."
                )
            }
        } else {
            val valid = paramTypes.isEmpty() ||
                    (paramTypes.size == 1 && TaskExecutionContext::class.java.isAssignableFrom(paramTypes[0]))
            if (!valid) {
                val paramNames = paramTypes.joinToString { it.simpleName }
                throw TaskConfigurationException(
                    "Scheduled method '$methodRef' has invalid parameters ($paramNames). Expected () or (TaskExecutionContext)."
                )
            }

            // Return type must be void / Unit
            val returnType = method.returnType
            if (returnType != Void.TYPE && returnType != Unit::class.java && returnType.name != "void") {
                throw TaskConfigurationException(
                    "Scheduled method '$methodRef' must have void/Unit return type, but returns ${returnType.name}."
                )
            }
        }
    }
}

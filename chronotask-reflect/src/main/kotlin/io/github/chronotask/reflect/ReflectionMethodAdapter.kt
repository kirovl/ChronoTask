package io.github.chronotask.reflect

import io.github.chronotask.job.TaskExecutable
import io.github.chronotask.job.TaskExecutionContext
import kotlinx.coroutines.CancellationException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspend
import kotlin.reflect.jvm.kotlinFunction
import kotlin.reflect.jvm.isAccessible

/**
 * Adapts a reflected Kotlin [KFunction] or Java [Method] into a suspending [TaskExecutable].
 */
internal class ReflectionMethodAdapter(
    private val instance: Any,
    private val method: Method
) : TaskExecutable {

    private val kFunction: KFunction<*>? = runCatching { method.kotlinFunction }.getOrNull()
    private val isSuspend: Boolean = kFunction?.isSuspend == true
    private val hasContextParam: Boolean = if (isSuspend && kFunction != null) {
        val valueParams = kFunction.parameters.filter { it.kind == kotlin.reflect.KParameter.Kind.VALUE }
        valueParams.size == 1 && (valueParams[0].type.classifier == TaskExecutionContext::class ||
                TaskExecutionContext::class.java.isAssignableFrom(method.parameterTypes[0]))
    } else {
        method.parameterCount == 1 && TaskExecutionContext::class.java.isAssignableFrom(method.parameterTypes[0])
    }

    init {
        if (!method.canAccess(instance)) {
            runCatching { method.isAccessible = true }
            runCatching { kFunction?.isAccessible = true }
        }
    }

    override suspend fun execute(context: TaskExecutionContext) {
        try {
            if (isSuspend && kFunction != null) {
                invokeSuspend(context)
            } else {
                invokeBlocking(context)
            }
        } catch (e: CancellationException) {
            // Must rethrow CancellationException directly for coroutine cancellation
            throw e
        } catch (e: InvocationTargetException) {
            val target = e.targetException ?: e
            if (target is CancellationException) {
                throw target
            }
            throw target
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            throw e
        }
    }

    private suspend fun invokeSuspend(context: TaskExecutionContext) {
        val fn = kFunction ?: error("KFunction must not be null for suspend execution")
        if (hasContextParam) {
            fn.callSuspend(instance, context)
        } else {
            fn.callSuspend(instance)
        }
    }

    private fun invokeBlocking(context: TaskExecutionContext) {
        if (hasContextParam) {
            method.invoke(instance, context)
        } else {
            method.invoke(instance)
        }
    }
}

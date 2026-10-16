package io.github.chronotask.compat

import io.github.chronotask.ChronoTask
import io.github.chronotask.builder.TaskBuilder
import io.github.chronotask.dsl.TaskDsl
import io.github.chronotask.engine.ChronoTaskEngine
import io.github.chronotask.engine.TaskHandle
import io.github.chronotask.job.ChronoJob
import io.github.chronotask.job.ManagedCronJob
import io.github.chronotask.model.TaskDefinition
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.lang.reflect.Method
import java.lang.reflect.Modifier

class PublicApiLeakTest {

    private val forbiddenClassNames = setOf(
        "io.github.chronotask.engine.TaskRuntime",
        "io.github.chronotask.engine.TaskScheduler",
        "io.github.chronotask.concurrency.TaskConcurrencyState",
        "io.github.chronotask.concurrency.DefaultConcurrencyController",
        "io.github.chronotask.execution.DefaultTaskRunner",
        "io.github.chronotask.misfire.DefaultMisfireResolver",
        "io.github.chronotask.engine.DefaultDispatcherProvider",
        "io.github.chronotask.engine.DefaultChronoTaskEngine",
        "io.github.chronotask.cron.CronFieldType",
        "kotlinx.coroutines.Job",
        "kotlinx.coroutines.sync.Mutex",
        "kotlinx.coroutines.CoroutineScope"
    )

    private val publicApiClasses = listOf(
        ChronoTask::class.java,
        ChronoTaskEngine::class.java,
        TaskHandle::class.java,
        TaskDefinition::class.java,
        TaskDsl::class.java,
        TaskBuilder::class.java,
        ChronoJob::class.java,
        ManagedCronJob::class.java
    )

    @Test
    fun `public API methods do not leak internal types or coroutine primitives in parameters or return types`() {
        for (clazz in publicApiClasses) {
            for (method in clazz.methods) {
                if (Modifier.isPublic(method.modifiers)) {
                    assertNoLeak(clazz, method)
                }
            }
        }
    }

    private fun assertNoLeak(clazz: Class<*>, method: Method) {
        val returnTypeName = method.returnType.name
        for (forbidden in forbiddenClassNames) {
            assertFalse(
                returnTypeName == forbidden,
                "Class ${clazz.simpleName} method ${method.name} leaks $forbidden as return type $returnTypeName"
            )
        }

        for (paramType in method.parameterTypes) {
            val paramTypeName = paramType.name
            for (forbidden in forbiddenClassNames) {
                assertFalse(
                    paramTypeName == forbidden,
                    "Class ${clazz.simpleName} method ${method.name} leaks $forbidden as parameter type $paramTypeName"
                )
            }
        }
    }
}

package io.github.chronotask.reflect.scanner

import io.github.chronotask.annotation.Scheduled
import io.github.chronotask.exception.TaskConfigurationException
import io.github.chronotask.job.TaskExecutionContext
import io.github.chronotask.reflect.ReflectionTaskScanner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReflectionSignatureValidationTest {

    class ValidService {
        @Scheduled(intervalMs = 1000, id = "no-arg")
        fun noArg() {}

        @Scheduled(intervalMs = 1000, id = "ctx-arg")
        fun withContext(ctx: TaskExecutionContext) {}

        @Scheduled(intervalMs = 1000, id = "suspend-no-arg")
        suspend fun suspendNoArg() {}

        @Scheduled(intervalMs = 1000, id = "suspend-ctx-arg")
        suspend fun suspendWithContext(ctx: TaskExecutionContext) {}
    }

    class InvalidParamTypeService {
        @Scheduled(intervalMs = 1000)
        fun invalidParam(param: String) {}
    }

    class InvalidMultipleParamsService {
        @Scheduled(intervalMs = 1000)
        fun invalidParams(ctx: TaskExecutionContext, extra: String) {}
    }

    class InvalidReturnTypeService {
        @Scheduled(intervalMs = 1000)
        fun invalidReturn(): String = "result"
    }

    class InvalidSuspendParamService {
        @Scheduled(intervalMs = 1000)
        suspend fun invalidSuspendParam(count: Int) {}
    }

    class InvalidSuspendStringService {
        @Scheduled(intervalMs = 1000)
        suspend fun invalidReturn(): String = "result"
    }

    class InvalidSuspendIntService {
        @Scheduled(intervalMs = 1000)
        suspend fun invalidReturn(ctx: TaskExecutionContext): Int = 42
    }

    class InvalidSuspendNullableStringService {
        @Scheduled(intervalMs = 1000)
        suspend fun invalidReturn(): String? = null
    }

    class InvalidSuspendGenericService {
        @Scheduled(intervalMs = 1000)
        suspend fun <T> invalidReturn(): T = error("Must never execute")
    }

    @Test
    fun `suspend methods with non-Unit return types fail with method diagnostics`() {
        for (service in listOf(InvalidSuspendStringService(), InvalidSuspendIntService(),
            InvalidSuspendNullableStringService(), InvalidSuspendGenericService())) {
            val error = assertThrows(TaskConfigurationException::class.java) {
                ReflectionTaskScanner.scan(service)
            }
            assertTrue(error.message!!.contains("#invalidReturn"))
            assertTrue(error.message!!.contains("must have Unit return type"))
        }
    }

    @Test
    fun `valid method signatures pass scanning`() {
        val service = ValidService()
        val definitions = ReflectionTaskScanner.scan(service)
        assertEquals(4, definitions.size)
    }

    @Test
    fun `invalid param type throws TaskConfigurationException`() {
        assertThrows(TaskConfigurationException::class.java) {
            ReflectionTaskScanner.scan(InvalidParamTypeService())
        }
    }

    @Test
    fun `multiple params throws TaskConfigurationException`() {
        assertThrows(TaskConfigurationException::class.java) {
            ReflectionTaskScanner.scan(InvalidMultipleParamsService())
        }
    }

    @Test
    fun `invalid return type throws TaskConfigurationException`() {
        assertThrows(TaskConfigurationException::class.java) {
            ReflectionTaskScanner.scan(InvalidReturnTypeService())
        }
    }

    @Test
    fun `invalid suspend param throws TaskConfigurationException`() {
        assertThrows(TaskConfigurationException::class.java) {
            ReflectionTaskScanner.scan(InvalidSuspendParamService())
        }
    }
}

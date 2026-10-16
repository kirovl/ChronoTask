package io.github.chronotask.reflect.scanner

import io.github.chronotask.annotation.Scheduled
import io.github.chronotask.exception.TaskConfigurationException
import io.github.chronotask.reflect.ReflectionTaskScanner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ReflectionVisibilityTest {

    class PublicAndProtectedService {
        @Scheduled(intervalMs = 1000, id = "pub-task")
        fun publicMethod() {}

        @Scheduled(intervalMs = 2000, id = "prot-task")
        protected fun protectedMethod() {}
    }

    class PrivateService {
        @Scheduled(intervalMs = 1000)
        private fun privateMethod() {}
    }

    class StaticMethodService {
        companion object {
            @JvmStatic
            @Scheduled(intervalMs = 1000)
            fun staticMethod() {}
        }
    }

    @Test
    fun `public and protected methods are supported`() {
        val service = PublicAndProtectedService()
        val definitions = ReflectionTaskScanner.scan(service)
        assertEquals(2, definitions.size)
    }

    @Test
    fun `private annotated method throws TaskConfigurationException`() {
        val service = PrivateService()
        assertThrows(TaskConfigurationException::class.java) {
            ReflectionTaskScanner.scan(service)
        }
    }

    @Test
    fun `static annotated method throws TaskConfigurationException`() {
        val service = StaticMethodService()
        assertThrows(TaskConfigurationException::class.java) {
            ReflectionTaskScanner.scan(service)
        }
    }
}

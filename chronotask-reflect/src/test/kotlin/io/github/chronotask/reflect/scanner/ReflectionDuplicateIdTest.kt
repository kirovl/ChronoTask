package io.github.chronotask.reflect.scanner

import io.github.chronotask.annotation.Scheduled
import io.github.chronotask.exception.TaskConfigurationException
import io.github.chronotask.job.TaskExecutionContext
import io.github.chronotask.reflect.ReflectionTaskScanner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ReflectionDuplicateIdTest {

    class DuplicateExplicitIdService {
        @Scheduled(intervalMs = 1000, id = "duplicate-id")
        fun taskA() {}

        @Scheduled(intervalMs = 2000, id = "duplicate-id")
        fun taskB() {}
    }

    class OverloadedMethodNoExplicitIdService {
        @Scheduled(intervalMs = 1000)
        fun cleanup() {}

        @Scheduled(intervalMs = 2000)
        fun cleanup(ctx: TaskExecutionContext) {}
    }

    class OverloadedMethodWithExplicitIdService {
        @Scheduled(intervalMs = 1000, id = "cleanup-no-arg")
        fun cleanup() {}

        @Scheduled(intervalMs = 2000, id = "cleanup-with-ctx")
        fun cleanup(ctx: TaskExecutionContext) {}
    }

    @Test
    fun `duplicate explicit ID throws TaskConfigurationException`() {
        assertThrows(TaskConfigurationException::class.java) {
            ReflectionTaskScanner.scan(DuplicateExplicitIdService())
        }
    }

    @Test
    fun `overloaded methods without explicit IDs collide on default ID and throw TaskConfigurationException`() {
        assertThrows(TaskConfigurationException::class.java) {
            ReflectionTaskScanner.scan(OverloadedMethodNoExplicitIdService())
        }
    }

    @Test
    fun `overloaded methods with unique explicit IDs pass scanning`() {
        val definitions = ReflectionTaskScanner.scan(OverloadedMethodWithExplicitIdService())
        assertEquals(2, definitions.size)
    }
}

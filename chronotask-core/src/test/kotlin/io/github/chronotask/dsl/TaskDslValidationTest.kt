package io.github.chronotask.dsl

import io.github.chronotask.exception.TaskConfigurationException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.seconds

class TaskDslValidationTest {

    @Test
    fun `blank task id throws TaskConfigurationException`() {
        assertThrows(TaskConfigurationException::class.java) {
            TaskDsl("")
        }
        assertThrows(TaskConfigurationException::class.java) {
            TaskDsl("   ")
        }
    }

    @Test
    fun `missing schedule throws TaskConfigurationException on build`() {
        val dsl = TaskDsl("no-schedule").apply {
            execute { /* no-op */ }
        }
        assertThrows(TaskConfigurationException::class.java) {
            dsl.build()
        }
    }

    @Test
    fun `missing executable throws TaskConfigurationException on build`() {
        val dsl = TaskDsl("no-executable").apply {
            fixedRate(5.seconds)
        }
        assertThrows(TaskConfigurationException::class.java) {
            dsl.build()
        }
    }

    @Test
    fun `configuring multiple schedules throws TaskConfigurationException`() {
        val dsl = TaskDsl("multi-schedule")
        dsl.fixedRate(5.seconds)
        assertThrows(TaskConfigurationException::class.java) {
            dsl.fixedDelay(10.seconds)
        }
    }

    @Test
    fun `configuring multiple executables throws TaskConfigurationException`() {
        val dsl = TaskDsl("multi-executable")
        dsl.execute { /* first */ }
        assertThrows(TaskConfigurationException::class.java) {
            dsl.execute { /* second */ }
        }
    }

    @Test
    fun `blank task name throws TaskConfigurationException on build`() {
        val dsl = TaskDsl("blank-name").apply {
            name = "   "
            fixedRate(5.seconds)
            execute { /* no-op */ }
        }
        assertThrows(TaskConfigurationException::class.java) {
            dsl.build()
        }
    }

    @Test
    fun `sub-millisecond duration throws TaskConfigurationException`() {
        val dsl = TaskDsl("sub-ms")
        assertThrows(TaskConfigurationException::class.java) {
            dsl.fixedRate(500.microseconds)
        }
    }
}

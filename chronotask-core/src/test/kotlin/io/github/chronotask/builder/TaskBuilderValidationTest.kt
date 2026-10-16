package io.github.chronotask.builder

import io.github.chronotask.exception.TaskConfigurationException
import io.github.chronotask.job.TaskExecutable
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Duration

class TaskBuilderValidationTest {

    @Test
    fun `blank task id throws TaskConfigurationException`() {
        assertThrows(TaskConfigurationException::class.java) {
            TaskBuilder("")
        }
        assertThrows(TaskConfigurationException::class.java) {
            TaskBuilder("   ")
        }
    }

    @Test
    fun `missing schedule throws TaskConfigurationException on build`() {
        val builder = TaskBuilder("no-schedule")
            .execute(TaskExecutable { /* no-op */ })
        assertThrows(TaskConfigurationException::class.java) {
            builder.build()
        }
    }

    @Test
    fun `missing executable throws TaskConfigurationException on build`() {
        val builder = TaskBuilder("no-executable")
            .fixedRate(Duration.ofSeconds(5))
        assertThrows(TaskConfigurationException::class.java) {
            builder.build()
        }
    }

    @Test
    fun `configuring multiple schedules throws TaskConfigurationException`() {
        val builder = TaskBuilder("multi-schedule")
            .fixedRate(Duration.ofSeconds(5))
        assertThrows(TaskConfigurationException::class.java) {
            builder.fixedDelay(Duration.ofSeconds(10))
        }
    }

    @Test
    fun `configuring multiple executables throws TaskConfigurationException`() {
        val builder = TaskBuilder("multi-exec")
            .execute(TaskExecutable { /* first */ })
        assertThrows(TaskConfigurationException::class.java) {
            builder.executeRunnable { /* second */ }
        }
    }

    @Test
    fun `blank task name throws TaskConfigurationException on build`() {
        val builder = TaskBuilder("blank-name")
        assertThrows(TaskConfigurationException::class.java) {
            builder.name("   ")
        }
    }

    @Test
    fun `sub-millisecond duration throws TaskConfigurationException`() {
        val builder = TaskBuilder("sub-ms")
        assertThrows(TaskConfigurationException::class.java) {
            builder.fixedRate(Duration.ofNanos(500_000))
        }
    }

    @Test
    fun `invalid serial queue capacity throws TaskConfigurationException`() {
        val builder = TaskBuilder("serial-invalid")
        assertThrows(TaskConfigurationException::class.java) {
            builder.serial(0)
        }
        assertThrows(TaskConfigurationException::class.java) {
            builder.serial(-5)
        }
    }
}

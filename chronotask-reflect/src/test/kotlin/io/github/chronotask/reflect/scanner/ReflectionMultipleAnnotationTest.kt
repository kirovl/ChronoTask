package io.github.chronotask.reflect.scanner

import io.github.chronotask.annotation.Cron
import io.github.chronotask.annotation.Once
import io.github.chronotask.annotation.Scheduled
import io.github.chronotask.annotation.TickTask
import io.github.chronotask.exception.TaskConfigurationException
import io.github.chronotask.reflect.ReflectionTaskScanner
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ReflectionMultipleAnnotationTest {

    class ScheduledAndCronService {
        @Scheduled(intervalMs = 1000)
        @Cron(expression = "0 0 * * * ?")
        fun task() {}
    }

    class ScheduledAndOnceService {
        @Scheduled(intervalMs = 1000)
        @Once(delayMs = 1000)
        fun task() {}
    }

    class ScheduledAndTickService {
        @Scheduled(intervalMs = 1000)
        @TickTask(intervalMs = 1000)
        fun task() {}
    }

    class CronAndOnceService {
        @Cron(expression = "0 0 * * * ?")
        @Once(delayMs = 1000)
        fun task() {}
    }

    class CronAndTickService {
        @Cron(expression = "0 0 * * * ?")
        @TickTask(intervalMs = 1000)
        fun task() {}
    }

    class OnceAndTickService {
        @Once(delayMs = 1000)
        @TickTask(intervalMs = 1000)
        fun task() {}
    }

    @Test
    fun `multiple scheduling annotations on same method throw TaskConfigurationException`() {
        assertThrows(TaskConfigurationException::class.java) {
            ReflectionTaskScanner.scan(ScheduledAndCronService())
        }
        assertThrows(TaskConfigurationException::class.java) {
            ReflectionTaskScanner.scan(ScheduledAndOnceService())
        }
        assertThrows(TaskConfigurationException::class.java) {
            ReflectionTaskScanner.scan(ScheduledAndTickService())
        }
        assertThrows(TaskConfigurationException::class.java) {
            ReflectionTaskScanner.scan(CronAndOnceService())
        }
        assertThrows(TaskConfigurationException::class.java) {
            ReflectionTaskScanner.scan(CronAndTickService())
        }
        assertThrows(TaskConfigurationException::class.java) {
            ReflectionTaskScanner.scan(OnceAndTickService())
        }
    }
}

package io.github.chronotask.reflect.registration

import io.github.chronotask.annotation.Cron
import io.github.chronotask.annotation.Once
import io.github.chronotask.cron.CronDialect
import io.github.chronotask.cron.CronParseException
import io.github.chronotask.engine.DefaultChronoTaskEngine
import io.github.chronotask.engine.DefaultDispatcherProvider
import io.github.chronotask.engine.EngineConfig
import io.github.chronotask.engine.TaskLifecycleListener
import io.github.chronotask.exception.TaskConfigurationException
import io.github.chronotask.model.TaskDefinition
import io.github.chronotask.reflect.ReflectionTaskScanner
import io.github.chronotask.reflect.registerAnnotated
import io.github.chronotask.test.time.VirtualTimeSource
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class ReflectionCronBatchValidationTest {
    class QuartzFieldCountJobs {
        @Cron(id = "quartz-count", expression = "definitely-not-cron") fun run() {}
    }
    class QuartzQuestionListJobs {
        @Cron(id = "quartz-question", expression = "?,30 0 0 * * ?") fun run() {}
    }
    class UnixFieldCountJobs {
        @Cron(id = "unix-count", expression = "0 0 0 * * ?", dialect = CronDialect.UNIX) fun run() {}
    }
    class UnixRangeJobs {
        @Cron(id = "unix-range", expression = "60 0 * * *", dialect = CronDialect.UNIX) fun run() {}
    }
    enum class InvalidCase(val jobs: Any, val id: String, val dialect: CronDialect) {
        QUARTZ_COUNT(QuartzFieldCountJobs(), "quartz-count", CronDialect.QUARTZ),
        QUARTZ_QUESTION(QuartzQuestionListJobs(), "quartz-question", CronDialect.QUARTZ),
        UNIX_COUNT(UnixFieldCountJobs(), "unix-count", CronDialect.UNIX),
        UNIX_RANGE(UnixRangeJobs(), "unix-range", CronDialect.UNIX)
    }

    class QuartzBatchJobs {
        var calls = 0
        @Once(id = "batch-valid") fun aValid() { calls++ }
        @Cron(id = "batch-invalid", expression = "definitely-not-cron") fun zInvalid() {}
    }
    class UnixBatchJobs {
        var calls = 0
        @Once(id = "batch-valid") fun aValid() { calls++ }
        @Cron(id = "batch-invalid", expression = "60 0 * * *", dialect = CronDialect.UNIX) fun zInvalid() {}
    }

    @ParameterizedTest
    @EnumSource(InvalidCase::class)
    fun `scan rejects malformed Cron with task id and parser cause`(sample: InvalidCase) {
        val error = assertThrows(TaskConfigurationException::class.java) {
            ReflectionTaskScanner.scan(sample.jobs)
        }
        assertTrue(error.message!!.contains(sample.id))
        assertTrue(error.message!!.contains(sample.dialect.name))
        assertTrue(error.cause is CronParseException)
        assertEquals(sample.dialect, (error.cause as CronParseException).dialect)
    }

    @ParameterizedTest
    @EnumSource(CronDialect::class)
    fun `malformed batch has no registration callbacks or executions`(dialect: CronDialect) {
        val time = VirtualTimeSource()
        val registered = mutableListOf<String>()
        val unregistered = mutableListOf<String>()
        val listener = object : TaskLifecycleListener {
            override fun onRegistered(definition: TaskDefinition) { registered += definition.id }
            override fun onUnregistered(definition: TaskDefinition) { unregistered += definition.id }
        }
        val engine = DefaultChronoTaskEngine(EngineConfig(
            clock = time.clock, timer = time.timer,
            dispatcherProvider = DefaultDispatcherProvider(Dispatchers.Unconfined),
            lifecycleListener = listener
        ))
        val quartzJobs = QuartzBatchJobs()
        val unixJobs = UnixBatchJobs()
        val jobs = if (dialect == CronDialect.QUARTZ) quartzJobs else unixJobs
        try {
            engine.start()
            val error = assertThrows(TaskConfigurationException::class.java) { engine.registerAnnotated(jobs) }
            assertTrue(error.message!!.contains("batch-invalid"))
            assertTrue(registered.isEmpty(), "The batch must fail before the first registration")
            assertTrue(unregistered.isEmpty(), "No rollback should be needed for malformed Cron")
            assertNull(engine.getHandle("batch-valid"))
            assertNull(engine.getHandle("batch-invalid"))
            assertEquals(0, quartzJobs.calls + unixJobs.calls)
            assertEquals(0, time.waiterCount())
        } finally {
            engine.stop()
        }
    }
}

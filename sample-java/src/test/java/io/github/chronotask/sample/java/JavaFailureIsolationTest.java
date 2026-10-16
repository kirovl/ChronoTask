package io.github.chronotask.sample.java;

import io.github.chronotask.ChronoTask;
import io.github.chronotask.engine.ChronoTaskEngine;
import io.github.chronotask.engine.DefaultDispatcherProvider;
import io.github.chronotask.engine.EngineConfig;
import io.github.chronotask.model.EngineState;
import io.github.chronotask.test.time.VirtualTimeSource;
import kotlinx.coroutines.Dispatchers;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaFailureIsolationTest {

    @Test
    void testTaskExceptionDoesNotCrashEngine() {
        VirtualTimeSource timeSource = new VirtualTimeSource(Instant.parse("2026-01-01T00:00:00Z"));
        AtomicBoolean errorHandled = new AtomicBoolean(false);

        EngineConfig config = new EngineConfig(
                timeSource.getClock(),
                timeSource.getTimer(),
                new DefaultDispatcherProvider(Dispatchers.getUnconfined()),
                null,
                null,
                (definition, execution, throwable) -> {
                    errorHandled.set(true);
                },
                io.github.chronotask.execution.DefaultTaskRunner.INSTANCE,
                io.github.chronotask.misfire.DefaultMisfireResolver.INSTANCE
        );
        ChronoTaskEngine engine = ChronoTask.create(config);
        SampleJobs jobs = new SampleJobs();
        SampleJobs_ChronoTaskBinding.register(engine, jobs);

        engine.start();

        // Advance 2s to trigger attempt #1 (healthy)
        timeSource.advanceBy(Duration.ofSeconds(2));
        assertEquals(1, jobs.getFailureAttempts());

        // Advance 3s more (total 5s) to trigger attempt #2 (throws exception)
        timeSource.advanceBy(Duration.ofSeconds(3));
        assertEquals(2, jobs.getFailureAttempts());
        assertTrue(errorHandled.get(), "Error handler should catch the exception");
        assertEquals(EngineState.RUNNING, engine.getState(), "Engine must remain RUNNING despite task failure");

        // Advance 3s more (total 8s) to trigger attempt #3 (healthy again)
        timeSource.advanceBy(Duration.ofSeconds(3));
        assertEquals(3, jobs.getFailureAttempts());

        engine.stop();
        assertEquals(EngineState.STOPPED, engine.getState());
    }
}

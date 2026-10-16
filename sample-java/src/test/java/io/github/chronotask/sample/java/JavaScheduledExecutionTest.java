package io.github.chronotask.sample.java;

import io.github.chronotask.ChronoTask;
import io.github.chronotask.engine.ChronoTaskEngine;
import io.github.chronotask.engine.DefaultDispatcherProvider;
import io.github.chronotask.engine.EngineConfig;
import io.github.chronotask.engine.TaskHandle;
import io.github.chronotask.model.EngineState;
import io.github.chronotask.model.TaskState;
import io.github.chronotask.test.time.VirtualTimeSource;
import kotlinx.coroutines.Dispatchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaScheduledExecutionTest {

    private VirtualTimeSource timeSource;
    private ChronoTaskEngine engine;
    private SampleJobs jobs;
    private AtomicBoolean errorHandlerInvoked;

    @BeforeEach
    void setUp() {
        timeSource = new VirtualTimeSource(Instant.parse("2026-01-01T00:00:00Z"));
        errorHandlerInvoked = new AtomicBoolean(false);
        EngineConfig config = new EngineConfig(
                timeSource.getClock(),
                timeSource.getTimer(),
                new DefaultDispatcherProvider(Dispatchers.getUnconfined()),
                null,
                null,
                (definition, execution, throwable) -> errorHandlerInvoked.set(true),
                io.github.chronotask.execution.DefaultTaskRunner.INSTANCE,
                io.github.chronotask.misfire.DefaultMisfireResolver.INSTANCE
        );
        engine = ChronoTask.create(config);
        jobs = new SampleJobs();
    }

    @AfterEach
    void tearDown() {
        engine.stop();
    }

    @Test
    void testJavaAptBindingRegistrationAndExecution() {
        List<TaskHandle> handles = SampleJobs_ChronoTaskBinding.register(engine, jobs);
        assertEquals(8, handles.size(), "Should register 8 APT annotated tasks");

        engine.start();
        assertEquals(EngineState.RUNNING, engine.getState());

        // 1. Once task (delay 500ms)
        timeSource.advanceBy(Duration.ofMillis(500L));
        assertEquals(1, jobs.getWelcomeCount());

        // 2. FixedRate (2000ms, initial 1000ms) & Tick (1000ms)
        timeSource.advanceBy(Duration.ofMillis(500L)); // Total 1000ms
        assertEquals(1, jobs.getOrderCount());
        assertEquals(1, jobs.getTickCount());

        // 3. FixedDelay (1500ms, initial 1500ms)
        timeSource.advanceBy(Duration.ofMillis(500L)); // Total 1500ms
        assertEquals(1, jobs.getInventoryChecks());

        // 4. Advance to 5000ms (Cron */5 * * * * ?)
        timeSource.advanceBy(Duration.ofMillis(3500L)); // Total 5000ms
        assertTrue(jobs.getCronHeartbeats() >= 1, "Cron pulse should fire");

        // 5. Failure demo isolation
        assertTrue(jobs.getFailureAttempts() >= 1, "Failure demo should have attempted runs");

        engine.stop();
        assertEquals(EngineState.STOPPED, engine.getState());
    }
}

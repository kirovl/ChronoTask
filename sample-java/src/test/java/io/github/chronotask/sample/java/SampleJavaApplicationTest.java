package io.github.chronotask.sample.java;

import io.github.chronotask.ChronoTask;
import io.github.chronotask.engine.ChronoTaskEngine;
import io.github.chronotask.engine.DefaultDispatcherProvider;
import io.github.chronotask.engine.EngineConfig;
import io.github.chronotask.engine.TaskHandle;
import io.github.chronotask.test.time.VirtualTimeSource;
import kotlinx.coroutines.Dispatchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SampleJavaApplicationTest {

    private VirtualTimeSource timeSource;
    private ChronoTaskEngine engine;
    private SampleJobs jobs;

    @BeforeEach
    void setUp() {
        timeSource = new VirtualTimeSource(Instant.parse("2026-01-01T00:00:00Z"));
        EngineConfig config = new EngineConfig(
                timeSource.getClock(),
                timeSource.getTimer(),
                new DefaultDispatcherProvider(Dispatchers.getUnconfined()),
                null,
                null,
                null,
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
    void testSampleJavaAptBindingExecution() {
        List<TaskHandle> handles = SampleJobs_ChronoTaskBinding.register(engine, jobs);
        assertEquals(8, handles.size(), "Should have registered 8 APT annotated tasks");

        engine.start();

        // Welcome task (Once delayMs = 500L)
        timeSource.advanceBy(Duration.ofMillis(500L));
        assertEquals(1, jobs.getWelcomeCount());

        // Order sync (FixedRate 2000L, initialDelay 1000L) & Tick (1000L)
        timeSource.advanceBy(Duration.ofMillis(500L)); // Total 1000ms
        assertEquals(1, jobs.getOrderCount());
        assertEquals(1, jobs.getTickCount());

        // Advance further to 2000ms
        timeSource.advanceBy(Duration.ofMillis(1000L)); // Total 2000ms
        assertEquals(1, jobs.getInventoryChecks());
        assertEquals(2, jobs.getTickCount());

        // Advance to 5000ms (Cron heartbeat should have triggered at 5s)
        timeSource.advanceBy(Duration.ofMillis(3000L)); // Total 5000ms
        assertTrue(jobs.getCronHeartbeats() >= 1, "Cron heartbeat should have fired");
    }
}

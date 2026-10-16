package com.example.sample;

import io.github.chronotask.ChronoTask;
import io.github.chronotask.engine.ChronoTaskEngine;
import io.github.chronotask.engine.DefaultDispatcherProvider;
import io.github.chronotask.engine.EngineConfig;
import io.github.chronotask.engine.TaskHandle;
import io.github.chronotask.model.TaskDefinition;
import io.github.chronotask.test.time.VirtualTimeSource;
import kotlinx.coroutines.Dispatchers;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SampleCleanupJobTest {

    @Test
    public void testJavaConsumerGeneratedBinding() {
        SampleCleanupJob job = new SampleCleanupJob();
        List<TaskDefinition> definitions = SampleCleanupJob_ChronoTaskBinding.definitions(job);
        assertEquals(2, definitions.size());

        Instant baseTime = Instant.parse("2026-01-01T10:00:00Z");
        VirtualTimeSource timeSource = new VirtualTimeSource(baseTime);
        EngineConfig config = new EngineConfig(
                timeSource.getClock(),
                timeSource.getTimer(),
                new DefaultDispatcherProvider(Dispatchers.getUnconfined())
        );
        ChronoTaskEngine engine = ChronoTask.create(config);

        List<TaskHandle> handles = SampleCleanupJob_ChronoTaskBinding.register(engine, job);
        assertEquals(2, handles.size());

        engine.start();

        timeSource.advanceBy(Duration.ofSeconds(1));
        assertEquals(1, job.getRunCount());

        timeSource.advanceBy(Duration.ofSeconds(1));
        assertEquals(3, job.getRunCount()); // cleanup + contextTask triggered

        engine.stop();
    }
}

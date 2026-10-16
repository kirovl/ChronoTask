package io.github.chronotask.builder;

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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JavaBuilderEngineE2ETest {

    @Test
    public void testJavaBuilderEngineExecutionWithVirtualTime() throws Exception {
        Instant baseTime = Instant.parse("2026-01-01T12:00:00Z");
        VirtualTimeSource timeSource = new VirtualTimeSource(baseTime);

        EngineConfig config = new EngineConfig(
                timeSource.getClock(),
                timeSource.getTimer(),
                new DefaultDispatcherProvider(Dispatchers.getUnconfined())
        );

        ChronoTaskEngine engine = ChronoTask.create(config);
        List<Instant> executionTimes = new CopyOnWriteArrayList<>();

        TaskDefinition definition = ChronoTask.newTask("java-e2e-task")
                .fixedRate(Duration.ofSeconds(5), Duration.ofSeconds(5))
                .execute(ctx -> executionTimes.add(ctx.getTrigger().getScheduledAt()))
                .build();

        TaskHandle handle = engine.register(definition);
        assertEquals("java-e2e-task", handle.getTaskId());

        engine.start();

        timeSource.advanceBy(Duration.ofSeconds(4));
        assertEquals(0, executionTimes.size());

        timeSource.advanceBy(Duration.ofSeconds(1)); // T+5s
        assertEquals(1, executionTimes.size());
        assertEquals(Instant.parse("2026-01-01T12:00:05Z"), executionTimes.get(0));

        timeSource.advanceBy(Duration.ofSeconds(5)); // T+10s
        assertEquals(2, executionTimes.size());
        assertEquals(Instant.parse("2026-01-01T12:00:10Z"), executionTimes.get(1));

        engine.stop();
    }
}

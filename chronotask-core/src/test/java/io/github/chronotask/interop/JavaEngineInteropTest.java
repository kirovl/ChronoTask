package io.github.chronotask.interop;

import io.github.chronotask.engine.ChronoTaskEngine;
import io.github.chronotask.engine.EngineConfig;
import io.github.chronotask.engine.TaskHandle;
import io.github.chronotask.engine.TaskRuntimeSnapshot;
import io.github.chronotask.execution.SubmissionResult;
import io.github.chronotask.job.TaskExecutable;
import io.github.chronotask.model.EngineState;
import io.github.chronotask.model.TaskDefinition;
import io.github.chronotask.model.TaskSchedule;
import io.github.chronotask.model.TaskState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Java Engine and Lifecycle Interoperability Tests")
class JavaEngineInteropTest {

    @Test
    @DisplayName("Java can configure, start, pause, resume, and stop ChronoTaskEngine")
    void testEngineLifecycleFromJava() {
        EngineConfig config = new EngineConfig();
        ChronoTaskEngine engine = ChronoTaskEngine.create(config);

        assertEquals(EngineState.CREATED, engine.getState());
        engine.start();
        assertEquals(EngineState.RUNNING, engine.getState());
        engine.pause();
        assertEquals(EngineState.PAUSED, engine.getState());
        engine.resume();
        assertEquals(EngineState.RUNNING, engine.getState());
        engine.stop();
        assertEquals(EngineState.STOPPED, engine.getState());
    }

    @Test
    @DisplayName("Java can register tasks, triggerNow, inspect snapshots, and unregister")
    void testTaskRegistrationAndControlFromJava() {
        ChronoTaskEngine engine = ChronoTaskEngine.create();
        engine.start();

        AtomicInteger count = new AtomicInteger(0);
        TaskDefinition definition = new TaskDefinition(
                "java-engine-task",
                "Java Engine Task",
                new TaskSchedule.FixedRate(5, TimeUnit.SECONDS),
                TaskExecutable.fromJava(context -> count.incrementAndGet())
        );

        TaskHandle handle = engine.register(definition);
        assertNotNull(handle);
        assertEquals("java-engine-task", handle.getTaskId());

        TaskRuntimeSnapshot snapshot = handle.snapshot();
        assertNotNull(snapshot);
        assertEquals("java-engine-task", snapshot.getTaskId());

        SubmissionResult manualResult = handle.triggerNow();
        assertTrue(manualResult instanceof SubmissionResult.Started);

        boolean unregistered = engine.unregister("java-engine-task");
        assertTrue(unregistered);
        assertEquals(TaskState.CANCELLED, handle.getState());

        engine.stop();
    }
}

package io.github.chronotask.sample.java;

import io.github.chronotask.ChronoTask;
import io.github.chronotask.engine.ChronoTaskEngine;
import io.github.chronotask.model.EngineState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JavaShutdownTest {

    @Test
    void testEngineShutdownTerminalState() {
        ChronoTaskEngine engine = ChronoTask.create();
        SampleJobs jobs = new SampleJobs();
        SampleJobs_ChronoTaskBinding.register(engine, jobs);

        assertEquals(EngineState.CREATED, engine.getState());
        engine.start();
        assertEquals(EngineState.RUNNING, engine.getState());

        engine.stop();
        assertEquals(EngineState.STOPPED, engine.getState());
    }
}

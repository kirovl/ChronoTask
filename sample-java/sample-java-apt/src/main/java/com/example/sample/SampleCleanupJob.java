package com.example.sample;

import io.github.chronotask.annotation.Scheduled;
import io.github.chronotask.job.TaskExecutionContext;
import java.io.IOException;

public class SampleCleanupJob {

    private int runCount = 0;

    @Scheduled(intervalMs = 1000L, initialDelayMs = 1000L)
    public void cleanup() throws IOException {
        runCount++;
    }

    @Scheduled(id = "contextTask", intervalMs = 2000L, initialDelayMs = 2000L)
    public void cleanupWithContext(TaskExecutionContext context) {
        runCount++;
    }

    public int getRunCount() {
        return runCount;
    }
}

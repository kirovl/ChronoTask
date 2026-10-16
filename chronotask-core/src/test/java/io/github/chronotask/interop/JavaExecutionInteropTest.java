package io.github.chronotask.interop;

import io.github.chronotask.execution.DefaultTaskRunner;
import io.github.chronotask.execution.ExecutionId;
import io.github.chronotask.execution.ExecutionListener;
import io.github.chronotask.execution.ExecutionResult;
import io.github.chronotask.execution.SkipReason;
import io.github.chronotask.execution.TaskErrorHandler;
import io.github.chronotask.execution.TaskExecution;
import io.github.chronotask.job.JavaTaskExecutable;
import io.github.chronotask.job.TaskExecutable;
import io.github.chronotask.model.ConcurrencyPolicy;
import io.github.chronotask.model.TaskDefinition;
import io.github.chronotask.model.TaskSchedule;
import io.github.chronotask.trigger.ScheduledTrigger;
import io.github.chronotask.trigger.TriggerSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Java Execution and Runner Interoperability Tests")
class JavaExecutionInteropTest {

    private final ScheduledTrigger sampleTrigger = new ScheduledTrigger(
            Instant.parse("2026-01-01T10:00:00Z"),
            Instant.parse("2026-01-01T10:00:00Z"),
            TriggerSource.SCHEDULE
    );

    @Test
    @DisplayName("Java can construct TaskExecution and ExecutionId")
    void testTaskExecutionCreation() {
        ExecutionId id = ExecutionId.of("custom-exec-1");
        assertEquals("custom-exec-1", id.getValue());

        TaskExecution execution = new TaskExecution(id, "java-task-1", sampleTrigger, 1);
        assertEquals(id, execution.getExecutionId());
        assertEquals("java-task-1", execution.getTaskId());
        assertEquals(sampleTrigger, execution.getTrigger());
        assertEquals(1, execution.getAttempt());
    }

    @Test
    @DisplayName("Java can define TaskErrorHandler and ExecutionListener")
    void testErrorHandlerAndListener() {
        AtomicBoolean errorHandled = new AtomicBoolean(false);
        TaskErrorHandler errorHandler = (definition, execution, error) -> {
            errorHandled.set(true);
        };

        AtomicBoolean started = new AtomicBoolean(false);
        ExecutionListener listener = new ExecutionListener() {
            @Override
            public void onStarted(TaskDefinition definition, TaskExecution execution) {
                started.set(true);
            }
        };

        assertNotNull(errorHandler);
        assertNotNull(listener);
    }

    @Test
    @DisplayName("ExecutionResult static constants and factory methods are accessible from Java")
    void testExecutionResultHelpers() {
        assertEquals(ExecutionResult.Success.INSTANCE, ExecutionResult.SUCCESS);
        assertEquals(ExecutionResult.Cancelled.INSTANCE, ExecutionResult.CANCELLED);

        Exception ex = new RuntimeException("fail");
        ExecutionResult failed = ExecutionResult.failed(ex);
        assertTrue(failed instanceof ExecutionResult.Failed);
        assertEquals(ex, ((ExecutionResult.Failed) failed).getError());

        ExecutionResult skipped = ExecutionResult.skipped(SkipReason.ALREADY_RUNNING);
        assertTrue(skipped instanceof ExecutionResult.Skipped);
        assertEquals(SkipReason.ALREADY_RUNNING, ((ExecutionResult.Skipped) skipped).getReason());
    }
}

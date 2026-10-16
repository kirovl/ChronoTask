package io.github.chronotask.reflect.invocation;

import io.github.chronotask.execution.DefaultTaskRunner;
import io.github.chronotask.execution.ExecutionId;
import io.github.chronotask.execution.ExecutionResult;
import io.github.chronotask.execution.TaskExecution;
import io.github.chronotask.job.TaskExecutionContext;
import io.github.chronotask.model.TaskDefinition;
import io.github.chronotask.model.TaskSchedule;
import io.github.chronotask.reflect.ReflectionMethodAdapter;
import io.github.chronotask.trigger.ScheduledTrigger;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.Dispatchers;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class JavaReflectionInvocationTest {

    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");

    public static class JavaWorker {
        public final AtomicBoolean executed = new AtomicBoolean(false);
        public final AtomicReference<String> contextTaskId = new AtomicReference<>();

        public void simpleWork() {
            executed.set(true);
        }

        public void workWithContext(TaskExecutionContext ctx) throws IOException {
            contextTaskId.set(ctx.getTaskId());
            executed.set(true);
        }

        public void throwCheckedException() throws IOException {
            throw new IOException("java-io-error");
        }
    }

    @Test
    public void testJavaNoArgMethodInvocation() throws Throwable {
        JavaWorker worker = new JavaWorker();
        Method method = JavaWorker.class.getMethod("simpleWork");
        ReflectionMethodAdapter adapter = new ReflectionMethodAdapter(worker, method);

        TaskDefinition def = new TaskDefinition(
                "java-no-arg",
                "java-no-arg",
                new TaskSchedule.Once(0L),
                io.github.chronotask.model.ConcurrencyPolicy.SkipIfRunning.INSTANCE,
                io.github.chronotask.model.MisfirePolicy.SKIP,
                null,
                true,
                adapter
        );

        TaskExecution execution = new TaskExecution(
                ExecutionId.Companion.generate(),
                "java-no-arg",
                new ScheduledTrigger(now, now)
        );

        ExecutionResult result = BuildersKt.runBlocking(Dispatchers.getDefault(), (scope, continuation) ->
                DefaultTaskRunner.INSTANCE.run(def, execution, null, null, continuation)
        );

        assertTrue(result instanceof ExecutionResult.Success);
        assertTrue(worker.executed.get());
    }

    @Test
    public void testJavaContextMethodInvocation() throws Throwable {
        JavaWorker worker = new JavaWorker();
        Method method = JavaWorker.class.getMethod("workWithContext", TaskExecutionContext.class);
        ReflectionMethodAdapter adapter = new ReflectionMethodAdapter(worker, method);

        TaskDefinition def = new TaskDefinition(
                "java-ctx",
                "java-ctx",
                new TaskSchedule.Once(0L),
                io.github.chronotask.model.ConcurrencyPolicy.SkipIfRunning.INSTANCE,
                io.github.chronotask.model.MisfirePolicy.SKIP,
                null,
                true,
                adapter
        );

        TaskExecution execution = new TaskExecution(
                ExecutionId.Companion.generate(),
                "java-ctx",
                new ScheduledTrigger(now, now)
        );

        ExecutionResult result = BuildersKt.runBlocking(Dispatchers.getDefault(), (scope, continuation) ->
                DefaultTaskRunner.INSTANCE.run(def, execution, null, null, continuation)
        );

        assertTrue(result instanceof ExecutionResult.Success);
        assertTrue(worker.executed.get());
        assertEquals("java-ctx", worker.contextTaskId.get());
    }

    @Test
    public void testJavaCheckedExceptionUnwrapped() throws Throwable {
        JavaWorker worker = new JavaWorker();
        Method method = JavaWorker.class.getMethod("throwCheckedException");
        ReflectionMethodAdapter adapter = new ReflectionMethodAdapter(worker, method);

        TaskDefinition def = new TaskDefinition(
                "java-throw",
                "java-throw",
                new TaskSchedule.Once(0L),
                io.github.chronotask.model.ConcurrencyPolicy.SkipIfRunning.INSTANCE,
                io.github.chronotask.model.MisfirePolicy.SKIP,
                null,
                true,
                adapter
        );

        TaskExecution execution = new TaskExecution(
                ExecutionId.Companion.generate(),
                "java-throw",
                new ScheduledTrigger(now, now)
        );

        ExecutionResult result = BuildersKt.runBlocking(Dispatchers.getDefault(), (scope, continuation) ->
                DefaultTaskRunner.INSTANCE.run(def, execution, null, null, continuation)
        );

        assertTrue(result instanceof ExecutionResult.Failed);
        ExecutionResult.Failed failed = (ExecutionResult.Failed) result;
        assertTrue(failed.getError() instanceof IOException);
        assertEquals("java-io-error", failed.getError().getMessage());
    }
}

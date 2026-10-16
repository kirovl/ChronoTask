package io.github.chronotask.interop;

import io.github.chronotask.engine.*;
import io.github.chronotask.model.*;
import io.github.chronotask.job.TaskExecutable;
import io.github.chronotask.test.time.VirtualTimeSource;
import io.github.chronotask.execution.SubmissionResult;
import kotlin.Unit;
import kotlinx.coroutines.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class LifecycleRegressionTest {
    static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");
    DefaultChronoTaskEngine engine(VirtualTimeSource time) {
        return new DefaultChronoTaskEngine(new EngineConfig(time.getClock(), time.getTimer(), new DefaultDispatcherProvider(Dispatchers.getUnconfined())));
    }
    @Test void pauseResumeFixedDelayMustWaitForRunningExecution() {
        var time = new VirtualTimeSource(BASE);
        var engine = engine(time);
        var starts = new AtomicInteger();
        try {
            engine.register(new TaskDefinition("delay", "delay", new TaskSchedule.FixedDelay(1000), ConcurrencyPolicy.ALLOW,
                MisfirePolicy.SKIP, (ctx, continuation) -> {
                    starts.incrementAndGet();
                    return time.getTimer().delay(3000, continuation);
                }));
            engine.start(); time.advanceBy(500); engine.pause(); engine.resume(); time.advanceBy(500);
            assertEquals(1, starts.get(), "Automatic FixedDelay executions must wait for completion across pause/resume");
            time.advanceBy(2000);
            assertEquals(1, starts.get());
            time.advanceBy(1000);
            assertEquals(2, starts.get(), "Resumed scheduling must measure the delay from execution completion");
        } finally { engine.stop(); }
    }
    @Test void cancelMustNotStartQueuedExecution() {
        for (var policy : new ConcurrencyPolicy[] {
            ConcurrencyPolicy.QUEUE_ONE, new ConcurrencyPolicy.Serial(5, QueueOverflowPolicy.DROP_LATEST)
        }) {
        var time = new VirtualTimeSource(BASE);
        var engine = engine(time);
        var starts = new AtomicInteger();
        try {
            var handle = engine.register(new TaskDefinition("queue", "queue", new TaskSchedule.FixedRate(10000), policy,
                MisfirePolicy.SKIP, null, false, (ctx, continuation) -> {
                    if (starts.incrementAndGet() == 1) return time.getTimer().delay(3000, continuation);
                    return Unit.INSTANCE;
                }));
            assertInstanceOf(SubmissionResult.Started.class, handle.triggerNow());
            assertInstanceOf(SubmissionResult.Queued.class, handle.triggerNow());
            handle.cancel();
            assertEquals(1, starts.get(), "Cancellation must discard the pending task without running it");
        } finally { engine.stop(); }
        }
    }
    @Test void cancelledBeforeDispatchMustNotBlockReregisteredTask() throws Exception {
        var time = new VirtualTimeSource(BASE);
        var pool = Executors.newSingleThreadExecutor();
        var blockerStarted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        pool.submit(() -> { blockerStarted.countDown(); try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
        assertTrue(blockerStarted.await(2, TimeUnit.SECONDS));
        var dispatcher = ExecutorsKt.from(pool);
        var engine = new DefaultChronoTaskEngine(new EngineConfig(time.getClock(), time.getTimer(),
            new DefaultDispatcherProvider(Dispatchers.getUnconfined(), Map.of("pool", dispatcher))));
        try {
            var definition = new TaskDefinition("same-id", "same-id", new TaskSchedule.Once(0), ConcurrencyPolicy.SKIP_IF_RUNNING,
                MisfirePolicy.SKIP, "pool", false, TaskExecutable.fromRunnable(() -> {}));
            var oldHandle = engine.register(definition);
            assertInstanceOf(SubmissionResult.Started.class, oldHandle.triggerNow());
            engine.unregister("same-id");
            release.countDown();
            pool.submit(() -> {}).get(2, TimeUnit.SECONDS);
            var newHandle = engine.register(definition);
            assertInstanceOf(SubmissionResult.Started.class, newHandle.triggerNow(), "Old cancelled job must not retain its running slot");
        } finally { release.countDown(); engine.stop(); dispatcher.close(); pool.shutdownNow(); }
    }
    @Test void oldGenerationCompletionMustNotMutateNewRuntime() throws Exception {
        var time = new VirtualTimeSource(BASE);
        var pool = Executors.newSingleThreadExecutor();
        var dispatcher = ExecutorsKt.from(pool);
        var oldStarted = new CountDownLatch(1);
        var oldRelease = new CountDownLatch(1);
        var engine = new DefaultChronoTaskEngine(new EngineConfig(time.getClock(), time.getTimer(),
            new DefaultDispatcherProvider(Dispatchers.getUnconfined(), Map.of("old", dispatcher))));
        try {
            var old = engine.register(new TaskDefinition("generation", "old", new TaskSchedule.Once(0), ConcurrencyPolicy.ALLOW,
                MisfirePolicy.SKIP, "old", false, TaskExecutable.fromRunnable(() -> { oldStarted.countDown(); try { oldRelease.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } })));
            old.triggerNow(); assertTrue(oldStarted.await(2, TimeUnit.SECONDS));
            engine.unregister("generation");
            var current = engine.register(new TaskDefinition("generation", "new", new TaskSchedule.Once(0), ConcurrencyPolicy.ALLOW,
                MisfirePolicy.SKIP, null, false, (ctx, continuation) -> time.getTimer().delay(10000, continuation)));
            current.triggerNow(); assertEquals(1, current.snapshot().getActiveExecutions());
            oldRelease.countDown(); pool.submit(() -> {}).get(2, TimeUnit.SECONDS);
            assertEquals(1, current.snapshot().getActiveExecutions(), "Old completion must not decrement current generation execution count");
            assertNull(current.snapshot().getLastCompletedAt());
        } finally { oldRelease.countDown(); engine.stop(); dispatcher.close(); pool.shutdownNow(); }
    }
}

package io.github.chronotask.engine;

import io.github.chronotask.clock.ChronoClock;
import io.github.chronotask.engine.*;
import io.github.chronotask.model.*;
import io.github.chronotask.job.TaskExecutable;
import io.github.chronotask.execution.SubmissionResult;
import io.github.chronotask.test.time.VirtualTimeSource;
import kotlinx.coroutines.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AtomicLifecycleRegressionTest {
    static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");
    static Stream<ConcurrencyPolicy> policies() {
        return Stream.of(ConcurrencyPolicy.ALLOW, ConcurrencyPolicy.SKIP_IF_RUNNING,
            ConcurrencyPolicy.QUEUE_ONE, new ConcurrencyPolicy.Serial(4, QueueOverflowPolicy.REJECT));
    }

    @ParameterizedTest
    @MethodSource("policies")
    void triggerNowMustNotExecuteAfterCancellation(ConcurrencyPolicy policy) throws Exception {
        var time = new VirtualTimeSource(BASE);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        ChronoClock clock = () -> {
            if (Thread.currentThread().getName().equals("manual-submit")) {
                entered.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("clock timeout"); }
                catch (InterruptedException e) { throw new IllegalStateException(e); }
            }
            return time.now();
        };
        var engine = new DefaultChronoTaskEngine(new EngineConfig(clock, time.getTimer(), new DefaultDispatcherProvider(Dispatchers.getUnconfined())));
        var executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "manual-submit"));
        var executions = new AtomicInteger();
        try {
            var handle = engine.register(new TaskDefinition("manual", "manual", new TaskSchedule.Once(0), policy,
                MisfirePolicy.SKIP, null, false, TaskExecutable.fromRunnable(executions::incrementAndGet)));
            var pending = executor.submit(handle::triggerNow);
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            handle.cancel(); release.countDown();
            var result = pending.get(2, TimeUnit.SECONDS);
            assertEquals(0, executions.get(), "A cancelled handle must not launch an execution that was still being prepared");
            assertInstanceOf(SubmissionResult.Rejected.class, result);
        } finally { release.countDown(); engine.stop(); executor.shutdownNow(); }
    }
    @ParameterizedTest
    @MethodSource("policies")
    void unregisterMustNotCancelConcurrentReplacement(ConcurrencyPolicy policy) throws Exception {
        var time = new VirtualTimeSource(BASE);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        ChronoClock clock = () -> {
            if (Thread.currentThread().getName().startsWith("old-execution")) {
                entered.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("clock timeout"); }
                catch (InterruptedException e) { throw new IllegalStateException(e); }
            }
            return time.now();
        };
        var oldPool = Executors.newSingleThreadExecutor(r -> new Thread(r, "old-execution"));
        var dispatcher = ExecutorsKt.from(oldPool);
        var engine = new DefaultChronoTaskEngine(new EngineConfig(clock, time.getTimer(),
            new DefaultDispatcherProvider(Dispatchers.getUnconfined(), Map.of("old", dispatcher))));
        var unregisterPool = Executors.newSingleThreadExecutor();
        try {
            var old = engine.register(new TaskDefinition("same", "old", new TaskSchedule.Once(0), policy,
                MisfirePolicy.SKIP, "old", false, TaskExecutable.fromRunnable(() -> {})));
            old.triggerNow(); assertTrue(entered.await(2, TimeUnit.SECONDS));
            var unregister = unregisterPool.submit(() -> engine.unregister("same"));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (engine.getHandle("same") != null && System.nanoTime() < deadline) Thread.onSpinWait();
            assertNull(engine.getHandle("same"));
            var replacement = engine.register(new TaskDefinition("same", "new", new TaskSchedule.Once(0), policy,
                MisfirePolicy.SKIP, null, false, (ctx, continuation) -> time.getTimer().delay(10000, continuation)));
            replacement.triggerNow(); assertEquals(1, replacement.snapshot().getActiveExecutions());
            release.countDown(); assertTrue(unregister.get(2, TimeUnit.SECONDS));
            assertEquals(1, replacement.snapshot().getActiveExecutions(), "Retiring an old runtime must not cancel its replacement");
        } finally { release.countDown(); engine.stop(); dispatcher.close(); oldPool.shutdownNow(); unregisterPool.shutdownNow(); }
    }
}

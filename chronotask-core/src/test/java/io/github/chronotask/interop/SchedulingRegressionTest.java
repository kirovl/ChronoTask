package io.github.chronotask.interop;

import io.github.chronotask.engine.*;
import io.github.chronotask.model.*;
import io.github.chronotask.job.TaskExecutable;
import io.github.chronotask.test.time.VirtualTimeSource;
import io.github.chronotask.trigger.TriggerSource;
import kotlinx.coroutines.*;
import kotlin.coroutines.CoroutineContext;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class SchedulingRegressionTest {
    static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");
    DefaultChronoTaskEngine engine(VirtualTimeSource time, CoroutineDispatcher dispatcher) {
        return new DefaultChronoTaskEngine(new EngineConfig(time.getClock(), time.getTimer(), new DefaultDispatcherProvider(dispatcher)));
    }
    @Test void catchUpShouldCapTotalMissedExecutions() {
        var time = new VirtualTimeSource(BASE);
        var engine = engine(time, Dispatchers.getUnconfined());
        var missed = new AtomicInteger();
        try {
            engine.register(new TaskDefinition("catchup", "catchup", new TaskSchedule.FixedRate(1000), ConcurrencyPolicy.ALLOW,
                new MisfirePolicy.CatchUp(5), TaskExecutable.fromJava(ctx -> {
                    if (ctx.getTrigger().getSource() == TriggerSource.MISFIRE) missed.incrementAndGet();
                })));
            engine.start(); engine.pause(); time.advanceBy(20500); engine.resume();
            assertEquals(5, missed.get(), "CatchUp(5) must cap total compensation");
            assertEquals(BASE.plusSeconds(20), engine.getHandle("catchup").snapshot().getLastScheduledAt());
            time.advanceBy(500);
            assertEquals(BASE.plusSeconds(21), engine.getHandle("catchup").snapshot().getLastScheduledAt());
            assertEquals(5, missed.get(), "The next regular trigger must not replay omitted compensation");
        } finally { engine.stop(); }
    }
    @Test void completedFixedDelayMustScheduleNextRun() {
        var time = new VirtualTimeSource(BASE);
        var inline = new CoroutineDispatcher() {
            @Override public void dispatch(CoroutineContext context, Runnable block) { block.run(); }
        };
        var engine = engine(time, inline);
        var count = new AtomicInteger();
        try {
            engine.register(new TaskDefinition("delay", new TaskSchedule.FixedDelay(1000), TaskExecutable.fromRunnable(count::incrementAndGet)));
            engine.start(); time.advanceBy(3000);
            assertEquals(4, count.get(), "Completed executions must not lose feedback");
        } finally { engine.stop(); }
    }
    @Test void skippedFixedDelayMustRemainRecurring() {
        var time = new VirtualTimeSource(BASE);
        var engine = engine(time, Dispatchers.getUnconfined());
        var count = new AtomicInteger();
        try {
            var handle = engine.register(new TaskDefinition("skip", new TaskSchedule.FixedDelay(1000), TaskExecutable.fromRunnable(count::incrementAndGet)));
            time.advanceBy(1); engine.start();
            assertFalse(handle.snapshot().getScheduleCompleted(), "A 1 ms late recurring schedule must not terminate");
            assertEquals(0, count.get());
            time.advanceBy(3000);
            assertEquals(3, count.get(), "Skipped recurring tasks must resume on the reanchored timeline");
        } finally { engine.stop(); }
    }
    @Test void namedDispatcherMustRunTask() throws Exception {
        var time = new VirtualTimeSource(BASE);
        var namedThread = new AtomicReference<Thread>();
        var pool = Executors.newSingleThreadExecutor(r -> {
            var thread = new Thread(r, "regression-named-pool");
            namedThread.set(thread);
            return thread;
        });
        var named = ExecutorsKt.from(pool);
        var provider = new DefaultDispatcherProvider(Dispatchers.getUnconfined(), Map.of("io", named));
        var engine = new DefaultChronoTaskEngine(new EngineConfig(time.getClock(), time.getTimer(), provider));
        var done = new CountDownLatch(1);
        var thread = new AtomicReference<Thread>();
        try {
            engine.register(new TaskDefinition("named", "named", new TaskSchedule.Once(0), ConcurrencyPolicy.ALLOW, MisfirePolicy.FIRE_ONCE,
                "io", true, TaskExecutable.fromRunnable(() -> { thread.set(Thread.currentThread()); done.countDown(); })));
            engine.start(); assertTrue(done.await(2, TimeUnit.SECONDS));
            assertSame(namedThread.get(), thread.get());
        } finally { engine.stop(); named.close(); pool.shutdownNow(); }
    }
}

package io.github.chronotask.interop;

import io.github.chronotask.engine.*;
import io.github.chronotask.model.*;
import io.github.chronotask.job.TaskExecutable;
import io.github.chronotask.test.time.VirtualTimeSource;
import io.github.chronotask.execution.ExecutionListener;
import io.github.chronotask.execution.TaskExecution;
import io.github.chronotask.trigger.TriggerSource;
import kotlinx.coroutines.Dispatchers;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryRegressionTest {
    static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");
    @Test void cronMustRecoverAfterThreeHoursPaused() {
        for (var policy : new MisfirePolicy[] { MisfirePolicy.SKIP, MisfirePolicy.FIRE_ONCE, new MisfirePolicy.CatchUp(3) }) {
            var time = new VirtualTimeSource(BASE);
            var engine = new DefaultChronoTaskEngine(new EngineConfig(time.getClock(), time.getTimer(), new DefaultDispatcherProvider(Dispatchers.getUnconfined())));
            var missed = new AtomicInteger();
            var regular = new AtomicInteger();
            try {
                var handle = engine.register(new TaskDefinition("cron", "cron", new TaskSchedule.Cron("* * * * * ?", ZoneId.of("UTC")),
                    ConcurrencyPolicy.ALLOW, policy, TaskExecutable.fromJava(ctx -> {
                        if (ctx.getTrigger().getSource() == TriggerSource.MISFIRE) missed.incrementAndGet();
                        else regular.incrementAndGet();
                    })));
                engine.start(); engine.pause(); time.advanceBy(10800500); engine.resume();
                assertNull(handle.snapshot().getScheduleFailure(), "A valid Cron schedule must recover after a three-hour pause");
                assertFalse(handle.snapshot().getScheduleCompleted());
                assertEquals(policy == MisfirePolicy.SKIP ? 0 : policy == MisfirePolicy.FIRE_ONCE ? 1 : 3, missed.get());
                time.advanceBy(500);
                assertEquals(1, regular.get());
            } finally { engine.stop(); }
        }
    }
    @Test void registrationMustNotInsertTasksAfterStop() throws Exception {
        var time = new VirtualTimeSource(BASE);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        DispatcherProvider provider = key -> {
            if (key != null) {
                entered.countDown();
                try { if (!release.await(2, TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }
            return Dispatchers.getUnconfined();
        };
        var engine = new DefaultChronoTaskEngine(new EngineConfig(time.getClock(), time.getTimer(), provider));
        var executor = Executors.newSingleThreadExecutor();
        try {
            var definition = new TaskDefinition("race", "race", new TaskSchedule.Once(0), ConcurrencyPolicy.ALLOW,
                MisfirePolicy.SKIP, "named", false, TaskExecutable.fromRunnable(() -> {}));
            var pending = executor.submit(() -> engine.register(definition));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            engine.stop(); release.countDown();
            var error = assertThrows(ExecutionException.class, () -> pending.get(2, TimeUnit.SECONDS));
            assertInstanceOf(EngineStateException.class, error.getCause());
            assertNull(engine.getHandle("race"));
        } finally { release.countDown(); engine.stop(); executor.shutdownNow(); }
    }
    @Test void assertionErrorMustNotLeaveFixedDelayWaitingForever() {
        var time = new VirtualTimeSource(BASE);
        var failures = new AtomicInteger();
        var engine = new DefaultChronoTaskEngine(new EngineConfig(time.getClock(), time.getTimer(),
            new DefaultDispatcherProvider(Dispatchers.getUnconfined()), null, new ExecutionListener() {
                @Override public void onFailure(TaskDefinition definition, TaskExecution execution, Throwable error) {
                    assertInstanceOf(AssertionError.class, error);
                    failures.incrementAndGet();
                }
            }));
        var starts = new AtomicInteger();
        try {
            var handle = engine.register(new TaskDefinition("error", new TaskSchedule.FixedDelay(1000), TaskExecutable.fromRunnable(() -> {
                if (starts.incrementAndGet() == 1) throw new AssertionError("user task assertion failed");
            })));
            engine.start(); time.advanceBy(3000);
            assertEquals(4, starts.get(), "Isolated task errors must not stall future scheduling");
            assertEquals(0, handle.snapshot().getActiveExecutions());
            assertEquals(1, failures.get(), "The failed execution must emit exactly one terminal event");
        } finally { engine.stop(); }
    }
}

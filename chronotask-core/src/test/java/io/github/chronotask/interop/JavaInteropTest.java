package io.github.chronotask.interop;

import io.github.chronotask.cron.CronDialect;
import io.github.chronotask.job.JavaTaskExecutable;
import io.github.chronotask.job.TaskExecutable;
import io.github.chronotask.job.TaskExecutionContext;
import io.github.chronotask.model.*;
import io.github.chronotask.trigger.ScheduledTrigger;
import io.github.chronotask.trigger.TriggerCalculator;
import io.github.chronotask.trigger.TriggerContext;
import io.github.chronotask.trigger.TriggerSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure Java test verifying seamless Java interoperability for all ChronoTask M1 public domain models and SPIs.
 */
public class JavaInteropTest {

    @Test
    public void testTaskScheduleFromJava() {
        // FixedRate
        TaskSchedule.FixedRate fixedRate1 = new TaskSchedule.FixedRate(5000L, 1000L);
        assertEquals(5000L, fixedRate1.getIntervalMs());
        assertEquals(1000L, fixedRate1.getInitialDelayMs());

        TaskSchedule.FixedRate fixedRate2 = new TaskSchedule.FixedRate(5, TimeUnit.SECONDS, 1, TimeUnit.SECONDS);
        assertEquals(5000L, fixedRate2.getIntervalMs());

        // FixedDelay
        TaskSchedule.FixedDelay fixedDelay = new TaskSchedule.FixedDelay(3, TimeUnit.MINUTES);
        assertEquals(180000L, fixedDelay.getDelayMs());

        // Cron
        ZoneId utc = ZoneId.of("UTC");
        TaskSchedule.Cron cron = new TaskSchedule.Cron("0 0 12 * * ?", utc);
        assertEquals("0 0 12 * * ?", cron.getExpression());
        assertEquals(utc, cron.getZoneId());
        assertEquals(CronDialect.QUARTZ, cron.getDialect());

        // Once
        TaskSchedule.Once once = new TaskSchedule.Once(2, TimeUnit.SECONDS);
        assertEquals(2000L, once.getDelayMs());

        // Custom TriggerCalculator
        TriggerCalculator calculator = context -> context.getNow().plusSeconds(10);
        TaskSchedule.Custom custom = new TaskSchedule.Custom(calculator);
        assertSame(calculator, custom.getCalculator());

        Instant now = Instant.now();
        TriggerContext triggerContext = new TriggerContext(now, now, null, null, null);
        Instant next = custom.getCalculator().nextTrigger(triggerContext);
        assertNotNull(next);
        assertEquals(now.plusSeconds(10), next);
    }

    @Test
    public void testConcurrencyPolicyFromJava() {
        assertSame(ConcurrencyPolicy.Allow.INSTANCE, ConcurrencyPolicy.ALLOW);
        assertSame(ConcurrencyPolicy.SkipIfRunning.INSTANCE, ConcurrencyPolicy.SKIP_IF_RUNNING);
        assertSame(ConcurrencyPolicy.QueueOne.INSTANCE, ConcurrencyPolicy.QUEUE_ONE);

        ConcurrencyPolicy.Serial serial = ConcurrencyPolicy.serial(50, QueueOverflowPolicy.DROP_LATEST);
        assertEquals(50, serial.getMaxQueueSize());
        assertEquals(QueueOverflowPolicy.DROP_LATEST, serial.getOverflowPolicy());
    }

    @Test
    public void testMisfirePolicyFromJava() {
        assertSame(MisfirePolicy.Skip.INSTANCE, MisfirePolicy.SKIP);
        assertSame(MisfirePolicy.FireOnce.INSTANCE, MisfirePolicy.FIRE_ONCE);

        MisfirePolicy.CatchUp catchUp = MisfirePolicy.catchUp(25);
        assertEquals(25, catchUp.getMaxExecutions());
    }

    @Test
    public void testStateAndSourceEnumsFromJava() {
        assertEquals(EngineState.CREATED, EngineState.valueOf("CREATED"));
        assertEquals(TaskState.SCHEDULED, TaskState.valueOf("SCHEDULED"));
        assertEquals(ExecutionStatus.SUCCESS, ExecutionStatus.valueOf("SUCCESS"));
        assertEquals(TriggerSource.MANUAL, TriggerSource.valueOf("MANUAL"));
        assertEquals(QueueOverflowPolicy.DROP_OLDEST, QueueOverflowPolicy.valueOf("DROP_OLDEST"));
    }

    @Test
    public void testTaskDefinitionAndJavaExecutableAdapter() {
        AtomicBoolean executed = new AtomicBoolean(false);

        // Java developer writing a clean synchronous lambda without Continuation
        JavaTaskExecutable javaExecutable = context -> {
            assertNotNull(context.getTaskId());
            executed.set(true);
        };

        TaskExecutable taskExecutable = TaskExecutable.fromJava(javaExecutable);
        assertNotNull(taskExecutable);

        TaskDefinition definition = new TaskDefinition(
                "order-cleanup",
                "Order Cleanup Task",
                new TaskSchedule.FixedRate(10, TimeUnit.SECONDS),
                ConcurrencyPolicy.SKIP_IF_RUNNING,
                MisfirePolicy.SKIP,
                null,
                true,
                taskExecutable
        );

        assertEquals("order-cleanup", definition.getId());
        assertEquals("Order Cleanup Task", definition.getName());
        assertTrue(definition.isEnabled());
        assertSame(taskExecutable, definition.getExecutable());

        // Also test Runnable adapter
        TaskExecutable fromRunnable = TaskExecutable.fromRunnable(() -> {});
        assertNotNull(fromRunnable);
    }

    @Test
    public void testTaskExecutionContextFromJava() {
        Instant now = Instant.now();
        ScheduledTrigger trigger = new ScheduledTrigger(now, now, TriggerSource.SCHEDULE);
        TaskExecutionContext context = new TaskExecutionContext("sync-job", trigger, 1);

        assertEquals("sync-job", context.getTaskId());
        assertEquals(trigger, context.getTrigger());
        assertEquals(1, context.getAttempt());
    }
}

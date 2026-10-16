package io.github.chronotask.builder;

import io.github.chronotask.ChronoTask;
import io.github.chronotask.cron.CronDialect;
import io.github.chronotask.exception.TaskConfigurationException;
import io.github.chronotask.model.ConcurrencyPolicy;
import io.github.chronotask.model.MisfirePolicy;
import io.github.chronotask.model.TaskDefinition;
import io.github.chronotask.model.TaskSchedule;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class JavaTaskBuilderTest {

    @Test
    public void testBuildFixedRateWithDefaults() {
        TaskDefinition def = ChronoTask.newTask("java-task-1")
                .fixedRate(Duration.ofSeconds(10))
                .execute(ctx -> {})
                .build();

        assertEquals("java-task-1", def.getId());
        assertEquals("java-task-1", def.getName());
        assertTrue(def.getSchedule() instanceof TaskSchedule.FixedRate);
        TaskSchedule.FixedRate fixedRate = (TaskSchedule.FixedRate) def.getSchedule();
        assertEquals(10_000L, fixedRate.getIntervalMs());
        assertEquals(0L, fixedRate.getInitialDelayMs());
        assertEquals(ConcurrencyPolicy.SkipIfRunning.INSTANCE, def.getConcurrencyPolicy());
        assertEquals(MisfirePolicy.SKIP, def.getMisfirePolicy());
        assertNull(def.getDispatcherKey());
        assertTrue(def.isEnabled());
    }

    @Test
    public void testBuildFixedDelayWithFullConfig() {
        TaskDefinition def = ChronoTask.newTask("java-task-2")
                .name("Custom Name")
                .fixedDelay(Duration.ofSeconds(30), Duration.ofSeconds(5))
                .allowConcurrent()
                .onMisfireCatchUp(10)
                .dispatcher("io-dispatcher")
                .enabled(false)
                .executeRunnable(() -> {})
                .build();

        assertEquals("java-task-2", def.getId());
        assertEquals("Custom Name", def.getName());
        assertTrue(def.getSchedule() instanceof TaskSchedule.FixedDelay);
        TaskSchedule.FixedDelay delay = (TaskSchedule.FixedDelay) def.getSchedule();
        assertEquals(30_000L, delay.getDelayMs());
        assertEquals(5_000L, delay.getInitialDelayMs());
        assertEquals(ConcurrencyPolicy.Allow.INSTANCE, def.getConcurrencyPolicy());
        assertEquals(new MisfirePolicy.CatchUp(10), def.getMisfirePolicy());
        assertEquals("io-dispatcher", def.getDispatcherKey());
        assertFalse(def.isEnabled());
    }

    @Test
    public void testBuildCronSchedule() {
        TaskDefinition def = ChronoTask.newTask("cron-task")
                .cron("0 0 12 * * ?", ZoneId.of("UTC"), CronDialect.QUARTZ)
                .queueOne()
                .onMisfireFireOnce()
                .execute(ctx -> {})
                .build();

        assertTrue(def.getSchedule() instanceof TaskSchedule.Cron);
        TaskSchedule.Cron cron = (TaskSchedule.Cron) def.getSchedule();
        assertEquals("0 0 12 * * ?", cron.getExpression());
        assertEquals(ZoneId.of("UTC"), cron.getZoneId());
        assertEquals(CronDialect.QUARTZ, cron.getDialect());
        assertEquals(ConcurrencyPolicy.QueueOne.INSTANCE, def.getConcurrencyPolicy());
        assertEquals(MisfirePolicy.FIRE_ONCE, def.getMisfirePolicy());
    }

    @Test
    public void testBuildOnceSchedule() {
        TaskDefinition def = ChronoTask.newTask("once-task")
                .once(Duration.ofSeconds(2))
                .serial(5, io.github.chronotask.model.QueueOverflowPolicy.DROP_OLDEST)
                .onMisfireSkip()
                .executeRunnable(() -> {})
                .build();

        assertTrue(def.getSchedule() instanceof TaskSchedule.Once);
        TaskSchedule.Once once = (TaskSchedule.Once) def.getSchedule();
        assertEquals(2_000L, once.getDelayMs());
        assertEquals(new ConcurrencyPolicy.Serial(5, io.github.chronotask.model.QueueOverflowPolicy.DROP_OLDEST), def.getConcurrencyPolicy());
    }

    @Test
    public void testThrowsOnDuplicateSchedule() {
        TaskBuilder builder = ChronoTask.newTask("dup-schedule")
                .fixedRate(Duration.ofSeconds(1));

        assertThrows(TaskConfigurationException.class, () ->
                builder.fixedDelay(Duration.ofSeconds(2))
        );
    }
}

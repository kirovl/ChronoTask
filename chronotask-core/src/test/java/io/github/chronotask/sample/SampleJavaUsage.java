package io.github.chronotask.sample;

import io.github.chronotask.ChronoTask;
import io.github.chronotask.cron.CronDialect;
import io.github.chronotask.engine.ChronoTaskEngine;
import io.github.chronotask.engine.TaskHandle;
import io.github.chronotask.job.ChronoJob;
import io.github.chronotask.job.ManagedCronJob;
import io.github.chronotask.job.TaskExecutionContext;
import io.github.chronotask.model.TaskDefinition;
import io.github.chronotask.model.TaskSchedule;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZoneId;

public class SampleJavaUsage {

    public static class JavaBackupJob extends ChronoJob {
        @NotNull
        @Override
        public String getId() {
            return "java-backup";
        }

        @NotNull
        @Override
        public TaskSchedule getSchedule() {
            return new TaskSchedule.FixedDelay(60_000L, 5_000L);
        }

        @Override
        public Object execute(@NotNull TaskExecutionContext context, @NotNull kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
            System.out.println("Java backup executing at " + context.getTrigger().getScheduledAt());
            return kotlin.Unit.INSTANCE;
        }
    }

    public static class JavaDailySettlementJob extends ManagedCronJob {
        public JavaDailySettlementJob() {
            super("java-settlement", "0 30 23 * * ?", ZoneId.of("Asia/Shanghai"), CronDialect.QUARTZ);
        }

        @Override
        public Object execute(@NotNull TaskExecutionContext context, @NotNull kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
            System.out.println("Settlement job running at " + context.getTrigger().getScheduledAt());
            return kotlin.Unit.INSTANCE;
        }
    }

    @Test
    public void demonstrateJavaUsagePatterns() {
        ChronoTaskEngine engine = ChronoTask.create();

        // 1. Fluent Java Builder with Lambda
        TaskDefinition def1 = ChronoTask.newTask("java-heartbeat")
                .fixedRate(Duration.ofSeconds(10), Duration.ofSeconds(2))
                .skipIfRunning()
                .onMisfireSkip()
                .execute(ctx -> System.out.println("Heartbeat at " + ctx.getTrigger().getScheduledAt()))
                .build();
        TaskHandle handle1 = engine.register(def1);

        // 2. Fluent Java Builder with Runnable
        TaskDefinition def2 = ChronoTask.newTask("java-cleanup")
                .cron("0 0 * * * ?", ZoneId.of("UTC"), CronDialect.QUARTZ)
                .queueOne()
                .onMisfireFireOnce()
                .executeRunnable(() -> System.out.println("Cleaned up resources."))
                .build();
        TaskHandle handle2 = engine.register(def2);

        // 3. Registering Job instance
        TaskHandle handle3 = engine.register(new JavaBackupJob().toTaskDefinition());
        TaskHandle handle4 = engine.register(new JavaDailySettlementJob().toTaskDefinition());

        // Lifecycle methods
        engine.start();
        handle1.pause();
        handle1.resume();
        handle2.triggerNow();
        engine.stop();
    }
}

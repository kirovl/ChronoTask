package io.github.chronotask.sample.java;

import io.github.chronotask.ChronoTask;
import io.github.chronotask.engine.ChronoTaskEngine;
import io.github.chronotask.engine.EngineConfig;
import io.github.chronotask.engine.TaskHandle;
import io.github.chronotask.model.MisfirePolicy;
import io.github.chronotask.model.TaskDefinition;

import java.time.Duration;
import java.util.List;

/**
 * Pure Java 17+ Reference Application demonstrating ChronoTask.
 */
public class SampleJavaApplication {

    public static void main(String[] args) {
        // Parse CLI arguments (e.g. --duration=10s or --duration=10)
        long durationSeconds = parseDurationSeconds(args);

        System.out.println("==================================================================");
        System.out.println("           ChronoTask Java 17+ Reference Application              ");
        System.out.println("==================================================================");
        System.out.println("Version: 1.0.0");
        System.out.printf("Execution Mode: %s%n",
                durationSeconds > 0 ? ("Finite Duration (" + durationSeconds + " seconds)") : "Continuous (Ctrl+C to stop)");
        System.out.println("------------------------------------------------------------------");

        // 1. Create ChronoTask engine with custom error handler
        EngineConfig config = new EngineConfig(
                io.github.chronotask.clock.ChronoClock.SYSTEM,
                io.github.chronotask.clock.ChronoTimer.SYSTEM,
                new io.github.chronotask.engine.DefaultDispatcherProvider(),
                null,
                null,
                (definition, execution, throwable) -> {
                    SampleJobs.log(definition.getId(), "ERROR",
                            String.format("Handled error: %s", throwable.getMessage()));
                },
                io.github.chronotask.execution.DefaultTaskRunner.INSTANCE,
                io.github.chronotask.misfire.DefaultMisfireResolver.INSTANCE
        );
        ChronoTaskEngine engine = ChronoTask.create(config);

        // 2. Register APT annotated jobs via generated compile-time binding
        SampleJobs sampleJobs = new SampleJobs();
        List<TaskHandle> aptHandles = SampleJobs_ChronoTaskBinding.register(engine, sampleJobs);

        // 3. Register Programmatic tasks via Java TaskBuilder
        TaskDefinition builderTask = ChronoTask.newTask("java-builder-archive-task")
                .fixedRate(Duration.ofSeconds(3), Duration.ofSeconds(1))
                .skipIfRunning()
                .misfirePolicy(MisfirePolicy.FIRE_ONCE)
                .executeRunnable(() -> {
                    SampleJobs.log("java-builder-archive-task", "SUCCESS", "Archive cleanup batch executed");
                })
                .build();
        engine.register(builderTask);

        // 4. Concurrency Policy Demo Task (QueueOne)
        TaskDefinition queueOneTask = ChronoTask.newTask("java-builder-queue-one-task")
                .fixedRate(Duration.ofSeconds(6), Duration.ofSeconds(2))
                .queueOne()
                .executeRunnable(() -> {
                    SampleJobs.log("java-builder-queue-one-task", "SUCCESS", "QueueOne batch processor executed");
                })
                .build();
        engine.register(queueOneTask);

        // 5. Print registered task list
        System.out.println("Registered tasks:");
        System.out.println(" - order-sync-job                (APT: FixedRate 2s, initialDelay 1s)");
        System.out.println(" - inventory-check-job          (APT: FixedDelay 1.5s)");
        System.out.println(" - cron-heartbeat-job           (APT: Cron */5 * * * * ?)");
        System.out.println(" - welcome-job                  (APT: Once 500ms)");
        System.out.println(" - tick-beacon-job              (APT: TickTask 1s)");
        System.out.println(" - context-audit-job            (APT: FixedRate 4s with TaskExecutionContext)");
        System.out.println(" - failure-demo-job             (APT: FixedRate 3s, Error Isolation Demo)");
        System.out.println(" - slow-task-job                (APT: FixedRate 1s, SkipIfRunning Demo)");
        System.out.println(" - java-builder-archive-task    (Builder: FixedRate 3s, Misfire FireOnce)");
        System.out.println(" - java-builder-queue-one-task  (Builder: FixedRate 6s, QueueOne)");
        System.out.println("------------------------------------------------------------------");

        // 6. Graceful shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[ChronoTask-Java] Shutdown signal received. Stopping engine...");
            engine.stop();
            System.out.println("[ChronoTask-Java] ChronoTask Engine stopped cleanly. Goodbye!");
        }));

        // 7. Start scheduling engine
        System.out.println("[ChronoTask-Java] Starting engine...");
        engine.start();
        System.out.println("[ChronoTask-Java] Engine state: RUNNING. Tasks executing automatically.\n");

        if (durationSeconds > 0) {
            try {
                Thread.sleep(durationSeconds * 1000L);
            } catch (InterruptedException ignored) {}
            System.out.printf("%n[ChronoTask-Java] Target duration of %d seconds elapsed. Stopping engine...%n", durationSeconds);
            engine.stop();
            System.out.println("[ChronoTask-Java] Engine stopped successfully.");
        } else {
            try {
                Thread.currentThread().join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static long parseDurationSeconds(String[] args) {
        String env = System.getenv("SAMPLE_DURATION_SECONDS");
        if (env != null && !env.isBlank()) {
            try {
                return Long.parseLong(env.trim());
            } catch (NumberFormatException ignored) {}
        }

        String prop = System.getProperty("chronotask.sample.duration");
        if (prop != null && !prop.isBlank()) {
            try {
                return Long.parseLong(prop.trim());
            } catch (NumberFormatException ignored) {}
        }

        if (Boolean.getBoolean("chronotask.sample.autoexit")) {
            return 8L;
        }

        if (args != null) {
            for (String arg : args) {
                if (arg.startsWith("--duration=")) {
                    String raw = arg.substring("--duration=".length()).trim();
                    if (raw.endsWith("s") || raw.endsWith("S")) {
                        raw = raw.substring(0, raw.length() - 1);
                    }
                    try {
                        return Long.parseLong(raw);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return 0L;
    }
}

package io.github.chronotask.sample.java;

import io.github.chronotask.annotation.Cron;
import io.github.chronotask.annotation.Once;
import io.github.chronotask.annotation.ScheduleMode;
import io.github.chronotask.annotation.Scheduled;
import io.github.chronotask.annotation.TickTask;
import io.github.chronotask.cron.CronDialect;
import io.github.chronotask.job.TaskExecutionContext;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sample jobs demonstrating all ChronoTask annotation paradigms via Java APT (JSR-269).
 */
public class SampleJobs {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private final AtomicInteger orderCount = new AtomicInteger(0);
    private final AtomicInteger inventoryChecks = new AtomicInteger(0);
    private final AtomicInteger cronHeartbeats = new AtomicInteger(0);
    private final AtomicInteger tickCount = new AtomicInteger(0);
    private final AtomicInteger welcomeCount = new AtomicInteger(0);
    private final AtomicInteger failureAttempts = new AtomicInteger(0);
    private final AtomicInteger slowTaskCount = new AtomicInteger(0);

    public static void log(String taskId, String event, String detail) {
        String timestamp = TIME_FMT.format(Instant.now());
        String thread = Thread.currentThread().getName();
        System.out.printf("%s [%-22s] [%-16s] %-8s %s%n", timestamp, taskId, thread, event, detail);
    }

    /**
     * Fixed-rate order sync job: runs every 2 seconds with 1 second initial delay.
     */
    @Scheduled(id = "order-sync-job", intervalMs = 2000L, initialDelayMs = 1000L)
    public void syncOrders() {
        int count = orderCount.incrementAndGet();
        log("order-sync-job", "SUCCESS", String.format("Order sync processed batch #%d", count));
    }

    /**
     * Fixed-delay inventory check job: runs 1.5s after previous execution completes.
     */
    @Scheduled(id = "inventory-check-job", intervalMs = 1500L, initialDelayMs = 1500L, mode = ScheduleMode.FIXED_DELAY)
    public void checkInventory() {
        int count = inventoryChecks.incrementAndGet();
        log("inventory-check-job", "SUCCESS", String.format("Inventory check completed (#%d)", count));
    }

    /**
     * High-frequency Cron heartbeat: runs every 5 seconds (Quartz dialect).
     */
    @Cron(id = "cron-heartbeat-job", expression = "*/5 * * * * ?", dialect = CronDialect.QUARTZ)
    public void heartbeat() {
        int count = cronHeartbeats.incrementAndGet();
        log("cron-heartbeat-job", "SUCCESS", String.format("Cron pulse emitted (#%d)", count));
    }

    /**
     * One-shot initialization job: runs once 500ms after engine start.
     */
    @Once(id = "welcome-job", delayMs = 500L)
    public void welcome() {
        welcomeCount.incrementAndGet();
        log("welcome-job", "SUCCESS", "One-shot startup initialization completed");
    }

    /**
     * Tick task: high-precision rate-based periodic beacon.
     */
    @TickTask(id = "tick-beacon-job", intervalMs = 1000L, initialDelayMs = 1000L)
    public void tickBeacon() {
        int count = tickCount.incrementAndGet();
        log("tick-beacon-job", "SUCCESS", String.format("Tick beacon pulse (#%d)", count));
    }

    /**
     * Context-aware task: inspects scheduled execution context.
     */
    @Scheduled(id = "context-audit-job", intervalMs = 4000L, initialDelayMs = 2000L)
    public void auditContext(TaskExecutionContext context) {
        log("context-audit-job", "SUCCESS", String.format("Context audit: source=%s scheduledAt=%s",
                context.getTrigger().getSource(), context.getTrigger().getScheduledAt()));
    }

    /**
     * Failure demo task: throws an IOException every 2nd attempt to demonstrate error handling & engine survival.
     */
    @Scheduled(id = "failure-demo-job", intervalMs = 3000L, initialDelayMs = 2000L)
    public void failureDemo() throws IOException {
        int attempt = failureAttempts.incrementAndGet();
        if (attempt % 2 == 0) {
            log("failure-demo-job", "FAILED", String.format("Simulating transient I/O exception on attempt #%d", attempt));
            throw new IOException("Simulated network timeout in failure-demo-job (#" + attempt + ")");
        } else {
            log("failure-demo-job", "SUCCESS", String.format("Healthy execution on attempt #%d", attempt));
        }
    }

    /**
     * Slow task: interval is 1s, execution takes 2.5s.
     * Demonstrates SkipIfRunning (overlapping triggers are dropped without failing).
     */
    @Scheduled(id = "slow-task-job", intervalMs = 1000L, initialDelayMs = 1000L)
    public void slowTask() {
        int count = slowTaskCount.incrementAndGet();
        log("slow-task-job", "START", String.format("Starting heavy batch work #%d (takes ~2.5s)", count));
        try {
            Thread.sleep(2500L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log("slow-task-job", "SUCCESS", String.format("Completed heavy batch work #%d", count));
    }

    public int getOrderCount() { return orderCount.get(); }
    public int getInventoryChecks() { return inventoryChecks.get(); }
    public int getCronHeartbeats() { return cronHeartbeats.get(); }
    public int getTickCount() { return tickCount.get(); }
    public int getWelcomeCount() { return welcomeCount.get(); }
    public int getFailureAttempts() { return failureAttempts.get(); }
    public int getSlowTaskCount() { return slowTaskCount.get(); }
}

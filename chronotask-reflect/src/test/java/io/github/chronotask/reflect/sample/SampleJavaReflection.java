package io.github.chronotask.reflect.sample;

import io.github.chronotask.ChronoTask;
import io.github.chronotask.annotation.AnnotationConcurrency;
import io.github.chronotask.annotation.AnnotationMisfire;
import io.github.chronotask.annotation.Cron;
import io.github.chronotask.annotation.Once;
import io.github.chronotask.annotation.ScheduleMode;
import io.github.chronotask.annotation.Scheduled;
import io.github.chronotask.annotation.TickTask;
import io.github.chronotask.engine.ChronoTaskEngine;
import io.github.chronotask.engine.TaskHandle;
import io.github.chronotask.job.TaskExecutionContext;
import io.github.chronotask.reflect.AnnotatedTaskRegistrar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SampleJavaReflection {

    private ChronoTaskEngine engine;

    @BeforeEach
    public void setUp() {
        engine = ChronoTask.create();
        engine.start();
    }

    @AfterEach
    public void tearDown() {
        engine.stop();
    }

    public static class JavaSampleServices {
        public final AtomicInteger cleanupCount = new AtomicInteger(0);
        public final AtomicInteger reportCount = new AtomicInteger(0);
        public final AtomicInteger initCount = new AtomicInteger(0);
        public final AtomicInteger tickCount = new AtomicInteger(0);

        @Scheduled(
            id = "java-cleanup",
            mode = ScheduleMode.FIXED_DELAY,
            intervalMs = 50,
            initialDelayMs = 10,
            concurrency = AnnotationConcurrency.SKIP_IF_RUNNING,
            misfire = AnnotationMisfire.SKIP
        )
        public void runCleanup(TaskExecutionContext context) {
            cleanupCount.incrementAndGet();
        }

        @Cron(expression = "*/1 * * * * ?", id = "java-cron-report")
        public void generateReport() {
            reportCount.incrementAndGet();
        }

        @Once(delayMs = 10, id = "java-init-task")
        public void initDatabase() {
            initCount.incrementAndGet();
        }

        @TickTask(intervalMs = 50, id = "java-game-tick")
        public void onTick() {
            tickCount.incrementAndGet();
        }
    }

    @Test
    public void testJavaAnnotatedTaskRegistrationAndExecution() throws Exception {
        JavaSampleServices services = new JavaSampleServices();
        List<TaskHandle> handles = AnnotatedTaskRegistrar.register(engine, services);

        assertEquals(4, handles.size());
        assertNotNull(engine.getHandle("java-cleanup"));
        assertNotNull(engine.getHandle("java-cron-report"));
        assertNotNull(engine.getHandle("java-init-task"));
        assertNotNull(engine.getHandle("java-game-tick"));

        engine.getHandle("java-cleanup").triggerNow();
        engine.getHandle("java-init-task").triggerNow();
        engine.getHandle("java-game-tick").triggerNow();

        Thread.sleep(100);

        assertTrue(services.cleanupCount.get() >= 1);
        assertTrue(services.initCount.get() >= 1);
        assertTrue(services.tickCount.get() >= 1);
    }
}

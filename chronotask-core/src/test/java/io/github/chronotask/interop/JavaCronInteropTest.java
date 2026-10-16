package io.github.chronotask.interop;

import io.github.chronotask.cron.CronDialect;
import io.github.chronotask.cron.CronExpression;
import io.github.chronotask.cron.CronParser;
import io.github.chronotask.cron.CronTriggerCalculator;
import io.github.chronotask.engine.ChronoTaskEngine;
import io.github.chronotask.engine.TaskHandle;
import io.github.chronotask.model.TaskDefinition;
import io.github.chronotask.model.TaskSchedule;
import io.github.chronotask.model.TaskState;
import io.github.chronotask.trigger.TriggerCalculator;
import io.github.chronotask.trigger.TriggerCalculatorFactory;
import io.github.chronotask.trigger.TriggerContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Java Cron Interoperability Tests")
class JavaCronInteropTest {

    @Test
    @DisplayName("Java can parse Cron expressions and query matching")
    void testCronParserFromJava() {
        CronExpression quartzExpr = CronParser.parse("0 15 10 ? * MON-FRI", CronDialect.QUARTZ);
        assertNotNull(quartzExpr);
        assertEquals(CronDialect.QUARTZ, quartzExpr.getDialect());

        CronExpression unixExpr = CronParser.parse("15 10 * * 1-5", CronDialect.UNIX);
        assertNotNull(unixExpr);
        assertEquals(CronDialect.UNIX, unixExpr.getDialect());

        ZoneId utc = ZoneId.of("UTC");
        ZonedDateTime matchingTime = ZonedDateTime.of(2024, 9, 16, 10, 15, 0, 0, utc); // Monday
        assertTrue(quartzExpr.matches(matchingTime));
        assertTrue(unixExpr.matches(matchingTime));
    }

    @Test
    @DisplayName("Java can construct Cron schedule and execute CronTriggerCalculator")
    void testCronTriggerCalculatorFromJava() {
        ZoneId utc = ZoneId.of("UTC");
        TaskSchedule.Cron cronSchedule = new TaskSchedule.Cron("0 0 12 * * ?", utc, CronDialect.QUARTZ);

        TriggerCalculator calc = TriggerCalculatorFactory.create(cronSchedule);
        assertTrue(calc instanceof CronTriggerCalculator);

        Instant registered = Instant.parse("2026-01-01T10:00:00Z");
        TriggerContext ctx = new TriggerContext(registered, registered, null, null, null);

        Instant next = calc.nextTrigger(ctx);
        assertEquals(Instant.parse("2026-01-01T12:00:00Z"), next);
    }

    @Test
    @DisplayName("Java can register and run Cron tasks in ChronoTaskEngine")
    void testCronEngineFromJava() {
        ChronoTaskEngine engine = ChronoTaskEngine.create();
        ZoneId utc = ZoneId.of("UTC");
        AtomicInteger counter = new AtomicInteger(0);

        TaskDefinition definition = new TaskDefinition(
                "java-cron-task",
                "Java Cron Task",
                new TaskSchedule.Cron("0 0 12 * * ?", utc, CronDialect.QUARTZ),
                io.github.chronotask.job.TaskExecutable.fromJava(context -> {
                    counter.incrementAndGet();
                })
        );

        TaskHandle handle = engine.register(definition);
        assertEquals(TaskState.REGISTERED, handle.getState());

        engine.start();
        assertEquals(TaskState.SCHEDULED, handle.getState());

        // Manual triggerNow works with Cron schedule
        io.github.chronotask.execution.SubmissionResult manualResult = handle.triggerNow();
        assertTrue(manualResult instanceof io.github.chronotask.execution.SubmissionResult.Started);

        engine.stop();
    }
}

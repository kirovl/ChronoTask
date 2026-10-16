package io.github.chronotask.interop;

import io.github.chronotask.misfire.DefaultMisfireResolver;
import io.github.chronotask.misfire.MisfireContext;
import io.github.chronotask.misfire.MisfireResult;
import io.github.chronotask.model.MisfirePolicy;
import io.github.chronotask.model.TaskSchedule;
import io.github.chronotask.trigger.FixedDelayTriggerCalculator;
import io.github.chronotask.trigger.FixedRateTriggerCalculator;
import io.github.chronotask.trigger.OnceTriggerCalculator;
import io.github.chronotask.trigger.ScheduledTrigger;
import io.github.chronotask.trigger.TriggerCalculator;
import io.github.chronotask.trigger.TriggerCalculatorFactory;
import io.github.chronotask.trigger.TriggerContext;
import io.github.chronotask.trigger.TriggerService;
import io.github.chronotask.trigger.TriggerSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Java Trigger and Misfire Interoperability Tests")
class JavaTriggerInteropTest {

    @Test
    @DisplayName("Java can construct and execute TriggerCalculators")
    void testCalculatorsFromJava() {
        Instant registered = Instant.parse("2026-01-01T10:00:00Z");
        TriggerContext ctx = new TriggerContext(
                registered,
                registered,
                null,
                null,
                null
        );

        TaskSchedule.FixedRate rateSchedule = new TaskSchedule.FixedRate(5, TimeUnit.SECONDS);
        TriggerCalculator rateCalc = TriggerCalculatorFactory.create(rateSchedule);
        assertTrue(rateCalc instanceof FixedRateTriggerCalculator);
        assertEquals(registered, rateCalc.nextTrigger(ctx));

        TaskSchedule.FixedDelay delaySchedule = new TaskSchedule.FixedDelay(3, TimeUnit.SECONDS, 1, TimeUnit.SECONDS);
        TriggerCalculator delayCalc = TriggerCalculatorFactory.create(delaySchedule);
        assertTrue(delayCalc instanceof FixedDelayTriggerCalculator);
        assertEquals(registered.plusSeconds(1), delayCalc.nextTrigger(ctx));

        TaskSchedule.Once onceSchedule = new TaskSchedule.Once(2, TimeUnit.SECONDS);
        TriggerCalculator onceCalc = TriggerCalculatorFactory.create(onceSchedule);
        assertTrue(onceCalc instanceof OnceTriggerCalculator);
        assertEquals(registered.plusSeconds(2), onceCalc.nextTrigger(ctx));
    }

    @Test
    @DisplayName("Java can define custom TriggerCalculator with lambda")
    void testCustomTriggerCalculatorLambda() {
        Instant fixedTarget = Instant.parse("2026-09-18T12:00:00Z");
        TriggerCalculator customCalc = context -> fixedTarget;

        TaskSchedule.Custom customSchedule = new TaskSchedule.Custom(customCalc);
        TriggerCalculator resolved = TriggerCalculatorFactory.create(customSchedule);

        Instant registered = Instant.parse("2026-01-01T10:00:00Z");
        TriggerContext ctx = new TriggerContext(registered, registered, null, null, null);

        assertEquals(fixedTarget, resolved.nextTrigger(ctx));
    }

    @Test
    @DisplayName("Java can use TriggerService and MisfireResolver directly")
    void testTriggerServiceAndMisfireResolver() {
        TriggerService service = new TriggerService();
        Instant registered = Instant.parse("2026-01-01T10:00:00Z");
        Instant now = Instant.parse("2026-01-01T10:03:30Z");

        TaskSchedule.FixedRate schedule = new TaskSchedule.FixedRate(60000L);
        MisfireResult result = service.evaluate(
                schedule,
                MisfirePolicy.FIRE_ONCE,
                registered,
                null,
                null,
                null,
                now
        );

        assertNotNull(result);
        assertEquals(1, result.getTriggers().size());
        ScheduledTrigger trigger = result.getTriggers().get(0);
        assertEquals(TriggerSource.MISFIRE, trigger.getSource());
        assertEquals(Instant.parse("2026-01-01T10:03:00Z"), trigger.getScheduledAt());
        assertEquals(Instant.parse("2026-01-01T10:04:00Z"), result.getNextScheduledAt());
    }
}

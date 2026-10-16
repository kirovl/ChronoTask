package io.github.chronotask.trigger

import io.github.chronotask.cron.CronDialect
import io.github.chronotask.model.TaskSchedule
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.ZoneId

class TriggerCalculatorFactoryTest {

    @Test
    fun `creates FixedRateTriggerCalculator for FixedRate schedule`() {
        val calc = TriggerCalculatorFactory.create(TaskSchedule.FixedRate(1000L))
        assertInstanceOf(FixedRateTriggerCalculator::class.java, calc)
    }

    @Test
    fun `creates FixedDelayTriggerCalculator for FixedDelay schedule`() {
        val calc = TriggerCalculatorFactory.create(TaskSchedule.FixedDelay(1000L))
        assertInstanceOf(FixedDelayTriggerCalculator::class.java, calc)
    }

    @Test
    fun `creates OnceTriggerCalculator for Once schedule`() {
        val calc = TriggerCalculatorFactory.create(TaskSchedule.Once(1000L))
        assertInstanceOf(OnceTriggerCalculator::class.java, calc)
    }

    @Test
    fun `creates CustomTriggerCalculator for Custom schedule`() {
        val calc = TriggerCalculatorFactory.create(TaskSchedule.Custom { null })
        assertInstanceOf(CustomTriggerCalculator::class.java, calc)
    }

    @Test
    fun `creates CronTriggerCalculator for Cron schedule`() {
        val cron = TaskSchedule.Cron("0 * * * * ?", ZoneId.of("UTC"), CronDialect.QUARTZ)
        val calc = TriggerCalculatorFactory.create(cron)
        assertInstanceOf(io.github.chronotask.cron.CronTriggerCalculator::class.java, calc)
    }
}

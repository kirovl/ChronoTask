package io.github.chronotask.cron

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class CronParserQuartzTest {

    @Test
    fun `parses standard 6-field quartz expression`() {
        val expr = CronParser.parse("0 15 10 ? * *", CronDialect.QUARTZ)
        assertEquals(CronDialect.QUARTZ, expr.dialect)
        assertTrue(expr.seconds.get(0))
        assertFalse(expr.seconds.get(1))
        assertTrue(expr.minutes.get(15))
        assertTrue(expr.hours.get(10))
        assertTrue(expr.isDomQuestion)
        assertFalse(expr.isDowQuestion)
        assertTrue(expr.isDowWildcard)
        // Years default to 1970..2199
        assertTrue(expr.years.get(2024))
        assertTrue(expr.years.get(1970))
        assertTrue(expr.years.get(2199))
    }

    @Test
    fun `parses standard 7-field quartz expression with specific year`() {
        val expr = CronParser.parse("0 0 12 1 1 ? 2025", CronDialect.QUARTZ)
        assertTrue(expr.seconds.get(0))
        assertTrue(expr.minutes.get(0))
        assertTrue(expr.hours.get(12))
        assertTrue(expr.daysOfMonth.get(1))
        assertTrue(expr.months.get(1))
        assertTrue(expr.isDowQuestion)
        assertTrue(expr.years.get(2025))
        assertFalse(expr.years.get(2024))
        assertFalse(expr.years.get(2026))
    }

    @Test
    fun `parses month names case-insensitively`() {
        val expr = CronParser.parse("0 0 12 ? Jan,JUN,dec *", CronDialect.QUARTZ)
        assertTrue(expr.months.get(1))  // JAN
        assertTrue(expr.months.get(6))  // JUN
        assertTrue(expr.months.get(12)) // DEC
        assertFalse(expr.months.get(2))
    }

    @Test
    fun `parses day-of-week names and ranges`() {
        val expr = CronParser.parse("0 0 12 ? * MON-FRI", CronDialect.QUARTZ)
        // In Quartz: 1=SUN, 2=MON, 3=TUE, 4=WED, 5=THU, 6=FRI, 7=SAT
        assertFalse(expr.daysOfWeek.get(1)) // SUN
        assertTrue(expr.daysOfWeek.get(2))  // MON
        assertTrue(expr.daysOfWeek.get(3))  // TUE
        assertTrue(expr.daysOfWeek.get(4))  // WED
        assertTrue(expr.daysOfWeek.get(5))  // THU
        assertTrue(expr.daysOfWeek.get(6))  // FRI
        assertFalse(expr.daysOfWeek.get(7)) // SAT
    }

    @Test
    fun `parses step expressions and ranges with steps`() {
        val expr = CronParser.parse("0/15 10-30/5 8-18/2 ? * 1-5", CronDialect.QUARTZ)
        // Seconds: 0, 15, 30, 45
        assertTrue(expr.seconds.get(0))
        assertTrue(expr.seconds.get(15))
        assertTrue(expr.seconds.get(30))
        assertTrue(expr.seconds.get(45))
        assertFalse(expr.seconds.get(10))

        // Minutes: 10, 15, 20, 25, 30
        assertTrue(expr.minutes.get(10))
        assertTrue(expr.minutes.get(15))
        assertTrue(expr.minutes.get(20))
        assertTrue(expr.minutes.get(25))
        assertTrue(expr.minutes.get(30))
        assertFalse(expr.minutes.get(35))

        // Hours: 8, 10, 12, 14, 16, 18
        assertTrue(expr.hours.get(8))
        assertTrue(expr.hours.get(10))
        assertTrue(expr.hours.get(18))
        assertFalse(expr.hours.get(9))
    }

    @Test
    fun `matches ZonedDateTime correctly`() {
        val expr = CronParser.parse("0 30 14 ? * MON", CronDialect.QUARTZ)
        val zone = ZoneId.of("UTC")

        // 2024-09-16 is Monday
        val mondayMatch = ZonedDateTime.of(LocalDateTime.of(2024, 9, 16, 14, 30, 0), zone)
        assertTrue(expr.matches(mondayMatch))

        // Wrong second
        assertFalse(expr.matches(mondayMatch.withSecond(1)))

        // 2024-09-17 is Tuesday
        val tuesdayMismatch = ZonedDateTime.of(LocalDateTime.of(2024, 9, 17, 14, 30, 0), zone)
        assertFalse(expr.matches(tuesdayMismatch))
    }
}

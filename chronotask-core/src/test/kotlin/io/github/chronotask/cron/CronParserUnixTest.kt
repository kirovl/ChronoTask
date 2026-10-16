package io.github.chronotask.cron

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class CronParserUnixTest {

    @Test
    fun `parses standard 5-field unix expression`() {
        val expr = CronParser.parse("30 8 1 * *", CronDialect.UNIX)
        assertEquals(CronDialect.UNIX, expr.dialect)
        // Seconds implicitly 0
        assertTrue(expr.seconds.get(0))
        assertFalse(expr.seconds.get(1))
        assertTrue(expr.minutes.get(30))
        assertTrue(expr.hours.get(8))
        assertTrue(expr.daysOfMonth.get(1))
        assertTrue(expr.isDowWildcard)
        assertFalse(expr.isDomWildcard)
    }

    @Test
    fun `parses unix day-of-week 0 and 7 as Sunday`() {
        val expr0 = CronParser.parse("0 0 * * 0", CronDialect.UNIX)
        val expr7 = CronParser.parse("0 0 * * 7", CronDialect.UNIX)
        val exprSun = CronParser.parse("0 0 * * SUN", CronDialect.UNIX)

        // Quartz 1 = Sunday
        assertTrue(expr0.daysOfWeek.get(1))
        assertTrue(expr7.daysOfWeek.get(1))
        assertTrue(exprSun.daysOfWeek.get(1))

        assertFalse(expr0.daysOfWeek.get(2))
    }

    @Test
    fun `parses unix day-of-week range with wrap or bounds`() {
        val expr = CronParser.parse("0 0 * * 1-5", CronDialect.UNIX)
        // 1-5 in UNIX is MON-FRI (Quartz 2-6)
        assertFalse(expr.daysOfWeek.get(1)) // SUN
        assertTrue(expr.daysOfWeek.get(2))  // MON
        assertTrue(expr.daysOfWeek.get(3))  // TUE
        assertTrue(expr.daysOfWeek.get(4))  // WED
        assertTrue(expr.daysOfWeek.get(5))  // THU
        assertTrue(expr.daysOfWeek.get(6))  // FRI
        assertFalse(expr.daysOfWeek.get(7)) // SAT
    }

    @Test
    fun `unix matching applies OR semantics when both DOM and DOW are restricted`() {
        // Run on 1st of the month OR every Monday
        val expr = CronParser.parse("0 12 1 * 1", CronDialect.UNIX)
        val zone = ZoneId.of("UTC")

        // 2024-09-01 is Sunday (1st of month, but not Monday) -> MATCHES due to DOM
        val sep1 = ZonedDateTime.of(LocalDateTime.of(2024, 9, 1, 12, 0, 0), zone)
        assertTrue(expr.matches(sep1))

        // 2024-09-09 is Monday (9th of month, but is Monday) -> MATCHES due to DOW
        val sep9 = ZonedDateTime.of(LocalDateTime.of(2024, 9, 9, 12, 0, 0), zone)
        assertTrue(expr.matches(sep9))

        // 2024-09-02 is Monday and 2nd -> MATCHES (Monday)
        val sep2 = ZonedDateTime.of(LocalDateTime.of(2024, 9, 2, 12, 0, 0), zone)
        assertTrue(expr.matches(sep2))

        // 2024-09-03 is Tuesday and 3rd -> MISMATCH (neither 1st nor Monday)
        val sep3 = ZonedDateTime.of(LocalDateTime.of(2024, 9, 3, 12, 0, 0), zone)
        assertFalse(expr.matches(sep3))
    }

    @Test
    fun `starred day-of-month steps intersect day-of-week`() {
        val expr = CronParser.parse("0 0 */2 * MON", CronDialect.UNIX)
        assertTrue(expr.isDomWildcard)
        assertFalse(expr.matches(ZonedDateTime.parse("2026-01-01T00:00:00Z")))
        assertFalse(expr.matches(ZonedDateTime.parse("2026-01-12T00:00:00Z")))
        assertTrue(expr.matches(ZonedDateTime.parse("2026-01-05T00:00:00Z")))
    }

    @Test
    fun `starred day-of-week steps intersect day-of-month`() {
        val expr = CronParser.parse("0 0 1 * */2", CronDialect.UNIX)
        assertTrue(expr.isDowWildcard)
        assertFalse(expr.matches(ZonedDateTime.parse("2026-01-06T00:00:00Z")))
        assertTrue(expr.matches(ZonedDateTime.parse("2026-01-01T00:00:00Z")))
    }

    @Test
    fun `two starred day fields retain both step restrictions`() {
        val expr = CronParser.parse("0 0 */2 * */2", CronDialect.UNIX)
        assertFalse(expr.matches(ZonedDateTime.parse("2026-01-05T00:00:00Z")))
        assertFalse(expr.matches(ZonedDateTime.parse("2026-01-06T00:00:00Z")))
        assertTrue(expr.matches(ZonedDateTime.parse("2026-01-01T00:00:00Z")))
    }

    @Test
    fun `starred lists use AND while explicit ranges use OR`() {
        val starred = CronParser.parse("0 0 */2,2 * MON", CronDialect.UNIX)
        assertFalse(starred.matches(ZonedDateTime.parse("2026-01-01T00:00:00Z")))
        assertTrue(starred.matches(ZonedDateTime.parse("2026-02-02T00:00:00Z")))
        val ranged = CronParser.parse("0 0 1-31/2 * MON", CronDialect.UNIX)
        assertFalse(ranged.isDomWildcard)
        assertTrue(ranged.matches(ZonedDateTime.parse("2026-01-01T00:00:00Z")))
    }
}

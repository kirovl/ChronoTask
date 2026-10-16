package io.github.chronotask.cron

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class CronParserValidationTest {

    @ParameterizedTest
    @ValueSource(strings = [
        "?,30 0 0 * * ?", "30,? 0 0 * * ?", "0 ?,30 0 * * ?",
        "0 0 ?,12 * * ?", "0 0 0 * ?,JAN ?", "0 0 0 * * ? ?,2026",
        "0 0 0 ?,1 * ?", "0 0 0 ? * ?,MON", "0 0 0 ?/1 * ?",
        "0 0 0 ? * ?-MON"
    ])
    fun `rejects embedded question marks in all Quartz fields`(expression: String) {
        assertThrows(CronParseException::class.java) { CronParser.parse(expression) }
    }


    @Test
    fun `large steps include only range start without integer overflow`() {
        for (seconds in listOf("1/2147483647", "1-59/2147483647")) {
            val expr = CronParser.parse("$seconds * * * * ?")
            assertEquals(listOf(1), expr.seconds.stream().toArray().toList())
        }
        val years = CronParser.parse("0 0 0 1 1 ? */2147483647")
        assertEquals(listOf(1970), years.years.stream().toArray().toList())
        for (dow in listOf("1/2147483647", "1-7/2147483647")) {
            val expr = CronParser.parse("0 0 * * $dow", CronDialect.UNIX)
            assertEquals(listOf(2), expr.daysOfWeek.stream().toArray().toList())
        }
    }

    @Test
    fun `step landing exactly on range end is included`() {
        val expr = CronParser.parse("1-59/58 * * * * ?")
        assertEquals(listOf(1, 59), expr.seconds.stream().toArray().toList())
        val dow = CronParser.parse("0 0 * * 1-7/6", CronDialect.UNIX)
        assertEquals(listOf(1, 2), dow.daysOfWeek.stream().toArray().toList())
    }

    @Test
    fun `rejects unsupported characters L, W, hash`() {
        assertThrows(CronParseException::class.java) {
            CronParser.parse("0 0 12 ? * 5L", CronDialect.QUARTZ)
        }
        assertThrows(CronParseException::class.java) {
            CronParser.parse("0 0 12 15W * ?", CronDialect.QUARTZ)
        }
        assertThrows(CronParseException::class.java) {
            CronParser.parse("0 0 12 ? * 5#3", CronDialect.QUARTZ)
        }
    }

    @Test
    fun `allows month and dow names containing letters L or W`() {
        // JUL contains 'L', WED contains 'W'
        CronParser.parse("0 0 12 ? JUL WED", CronDialect.QUARTZ)
        CronParser.parse("0 12 * 7 3", CronDialect.UNIX)
    }

    @Test
    fun `rejects question mark in UNIX dialect`() {
        assertThrows(CronParseException::class.java) {
            CronParser.parse("0 12 ? * *", CronDialect.UNIX)
        }
    }

    @Test
    fun `rejects quartz without question mark or with question mark in both DOM and DOW`() {
        // Neither is '?'
        assertThrows(CronParseException::class.java) {
            CronParser.parse("0 0 12 * * *", CronDialect.QUARTZ)
        }
        // Both are '?'
        assertThrows(CronParseException::class.java) {
            CronParser.parse("0 0 12 ? * ?", CronDialect.QUARTZ)
        }
    }

    @Test
    fun `rejects invalid field counts`() {
        // 4 fields in Quartz
        assertThrows(CronParseException::class.java) {
            CronParser.parse("0 0 12 *", CronDialect.QUARTZ)
        }
        // 8 fields in Quartz
        assertThrows(CronParseException::class.java) {
            CronParser.parse("0 0 12 1 1 ? 2024 EXTRA", CronDialect.QUARTZ)
        }
        // 6 fields in UNIX
        assertThrows(CronParseException::class.java) {
            CronParser.parse("0 0 12 1 1 ?", CronDialect.UNIX)
        }
    }

    @Test
    fun `rejects out of bounds values`() {
        // Second 60
        assertThrows(CronParseException::class.java) {
            CronParser.parse("60 0 12 ? * *", CronDialect.QUARTZ)
        }
        // Hour 24
        assertThrows(CronParseException::class.java) {
            CronParser.parse("0 0 24 ? * *", CronDialect.QUARTZ)
        }
        // Month 13
        assertThrows(CronParseException::class.java) {
            CronParser.parse("0 0 12 ? 13 *", CronDialect.QUARTZ)
        }
        // Year 1969
        assertThrows(CronParseException::class.java) {
            CronParser.parse("0 0 12 ? * * 1969", CronDialect.QUARTZ)
        }
        // Year 2200
        assertThrows(CronParseException::class.java) {
            CronParser.parse("0 0 12 ? * * 2200", CronDialect.QUARTZ)
        }
        // DOW 0 in Quartz
        assertThrows(CronParseException::class.java) {
            CronParser.parse("0 0 12 ? * 0", CronDialect.QUARTZ)
        }
        // DOW 8 in UNIX
        assertThrows(CronParseException::class.java) {
            CronParser.parse("0 12 * * 8", CronDialect.UNIX)
        }
    }

    @Test
    fun `rejects invalid steps and reversed ranges`() {
        // Step 0
        assertThrows(CronParseException::class.java) {
            CronParser.parse("0/0 0 12 ? * *", CronDialect.QUARTZ)
        }
        // Negative step
        assertThrows(CronParseException::class.java) {
            CronParser.parse("0/-5 0 12 ? * *", CronDialect.QUARTZ)
        }
        // Reversed range
        assertThrows(CronParseException::class.java) {
            CronParser.parse("30-10 0 12 ? * *", CronDialect.QUARTZ)
        }
    }

    @Test
    fun `rejects empty or blank expressions`() {
        assertThrows(CronParseException::class.java) {
            CronParser.parse("", CronDialect.QUARTZ)
        }
        assertThrows(CronParseException::class.java) {
            CronParser.parse("   ", CronDialect.UNIX)
        }
    }
}

package io.github.chronotask.cron

import java.util.BitSet
import java.util.Locale

/**
 * High-performance, zero-allocation-hot-path parser for Cron expressions.
 * Supports Quartz (6 or 7 fields) and UNIX (5 fields) dialects.
 */
object CronParser {

    private val MONTH_NAMES = mapOf(
        "JAN" to 1, "FEB" to 2, "MAR" to 3, "APR" to 4,
        "MAY" to 5, "JUN" to 6, "JUL" to 7, "AUG" to 8,
        "SEP" to 9, "OCT" to 10, "NOV" to 11, "DEC" to 12
    )

    private val QUARTZ_DOW_NAMES = mapOf(
        "SUN" to 1, "MON" to 2, "TUE" to 3, "WED" to 4,
        "THU" to 5, "FRI" to 6, "SAT" to 7
    )

    private val UNIX_DOW_NAMES = mapOf(
        "SUN" to 0, "MON" to 1, "TUE" to 2, "WED" to 3,
        "THU" to 4, "FRI" to 5, "SAT" to 6
    )

    /**
     * Parses the given [expression] according to the specified [dialect].
     *
     * @param expression The cron expression string.
     * @param dialect The cron syntax dialect (defaults to [CronDialect.QUARTZ]).
     * @return A compiled, immutable [CronExpression].
     * @throws CronParseException if the expression is syntactically or semantically invalid.
     */
    @JvmStatic
    @JvmOverloads
    fun parse(expression: String, dialect: CronDialect = CronDialect.QUARTZ): CronExpression {
        val trimmed = expression.trim()
        if (trimmed.isEmpty()) {
            throw CronParseException(expression, dialect, "Cron expression must not be empty or blank")
        }

        val tokens = trimmed.split("\\s+".toRegex())

        return when (dialect) {
            CronDialect.QUARTZ -> parseQuartz(expression, tokens)
            CronDialect.UNIX -> parseUnix(expression, tokens)
        }
    }

    private fun parseQuartz(expression: String, tokens: List<String>): CronExpression {
        if (tokens.size != 6 && tokens.size != 7) {
            throw CronParseException(
                expression,
                CronDialect.QUARTZ,
                "Quartz cron expression must have 6 or 7 fields, but found ${tokens.size}"
            )
        }

        val secToken = tokens[0]
        val minToken = tokens[1]
        val hourToken = tokens[2]
        val domToken = tokens[3]
        val monthToken = tokens[4]
        val dowToken = tokens[5]
        val yearToken = if (tokens.size == 7) tokens[6] else "*"

        // Check unsupported special characters L, W, # in tokens
        checkUnsupportedSpecialChars(expression, CronDialect.QUARTZ, tokens)

        // '?' denotes the entire day field; it cannot be mixed with list/range/step values.
        if ((domToken.contains("?") && domToken != "?") || (dowToken.contains("?") && dowToken != "?")) {
            throw CronParseException(
                expression,
                CronDialect.QUARTZ,
                "'?' must be used alone in a day-of-month or day-of-week field"
            )
        }

        // Quartz requires '?' in either DOM or DOW, but not both
        val isDomQuestion = domToken == "?"
        val isDowQuestion = dowToken == "?"

        if (!isDomQuestion && !isDowQuestion) {
            throw CronParseException(
                expression,
                CronDialect.QUARTZ,
                "Quartz cron expression must specify '?' in either day-of-month or day-of-week"
            )
        }
        if (isDomQuestion && isDowQuestion) {
            throw CronParseException(
                expression,
                CronDialect.QUARTZ,
                "Quartz cron expression cannot specify '?' for both day-of-month and day-of-week"
            )
        }

        // '?' is not allowed in other fields
        if (listOf(secToken, minToken, hourToken, monthToken, yearToken).any { it.contains("?") }) {
            throw CronParseException(
                expression,
                CronDialect.QUARTZ,
                "'?' character is only supported in day-of-month and day-of-week fields"
            )
        }

        val seconds = parseField(expression, CronDialect.QUARTZ, secToken, CronFieldType.SECOND)
        val minutes = parseField(expression, CronDialect.QUARTZ, minToken, CronFieldType.MINUTE)
        val hours = parseField(expression, CronDialect.QUARTZ, hourToken, CronFieldType.HOUR)
        val daysOfMonth = parseField(expression, CronDialect.QUARTZ, domToken, CronFieldType.DAY_OF_MONTH)
        val months = parseField(expression, CronDialect.QUARTZ, monthToken, CronFieldType.MONTH)
        val daysOfWeek = parseField(expression, CronDialect.QUARTZ, dowToken, CronFieldType.DAY_OF_WEEK)
        val years = parseField(expression, CronDialect.QUARTZ, yearToken, CronFieldType.YEAR)

        return CronExpression(
            expression = expression,
            dialect = CronDialect.QUARTZ,
            seconds = seconds,
            minutes = minutes,
            hours = hours,
            daysOfMonth = daysOfMonth,
            months = months,
            daysOfWeek = daysOfWeek,
            years = years,
            isDomWildcard = domToken == "*",
            isDowWildcard = dowToken == "*",
            isDomQuestion = isDomQuestion,
            isDowQuestion = isDowQuestion
        )
    }

    private fun parseUnix(expression: String, tokens: List<String>): CronExpression {
        if (tokens.size != 5) {
            throw CronParseException(
                expression,
                CronDialect.UNIX,
                "UNIX cron expression must have exactly 5 fields, but found ${tokens.size}"
            )
        }

        val minToken = tokens[0]
        val hourToken = tokens[1]
        val domToken = tokens[2]
        val monthToken = tokens[3]
        val dowToken = tokens[4]

        // Check unsupported special characters
        checkUnsupportedSpecialChars(expression, CronDialect.UNIX, tokens)

        // '?' is not supported in UNIX
        if (tokens.any { it.contains("?") }) {
            throw CronParseException(
                expression,
                CronDialect.UNIX,
                "'?' character is not supported in UNIX cron dialect"
            )
        }

        val seconds = BitSet(60).apply { set(0) } // UNIX cron runs at second 0
        val minutes = parseField(expression, CronDialect.UNIX, minToken, CronFieldType.MINUTE)
        val hours = parseField(expression, CronDialect.UNIX, hourToken, CronFieldType.HOUR)
        val daysOfMonth = parseField(expression, CronDialect.UNIX, domToken, CronFieldType.DAY_OF_MONTH)
        val months = parseField(expression, CronDialect.UNIX, monthToken, CronFieldType.MONTH)
        val daysOfWeek = parseUnixDowField(expression, dowToken)
        val years = BitSet(2200).apply { set(1970, 2200) }

        return CronExpression(
            expression = expression,
            dialect = CronDialect.UNIX,
            seconds = seconds,
            minutes = minutes,
            hours = hours,
            daysOfMonth = daysOfMonth,
            months = months,
            daysOfWeek = daysOfWeek,
            years = years,
            isDomWildcard = domToken.startsWith("*"),
            isDowWildcard = dowToken.startsWith("*"),
            isDomQuestion = false,
            isDowQuestion = false
        )
    }

    private fun checkUnsupportedSpecialChars(
        expression: String,
        dialect: CronDialect,
        tokens: List<String>
    ) {
        for (token in tokens) {
            // Check for '#' anywhere
            if (token.contains("#")) {
                throw CronParseException(
                    expression,
                    dialect,
                    "Special character '#' (nth day of week) is not supported in Cron V1"
                )
            }
            // Check for 'L' or 'W' outside of valid month/dow names
            val upper = token.uppercase(Locale.ROOT)
            val withoutNames = upper
                .replace("JUL", "")
                .replace("WED", "")

            if (withoutNames.contains("L")) {
                throw CronParseException(
                    expression,
                    dialect,
                    "Special character 'L' (last) is not supported in Cron V1"
                )
            }
            if (withoutNames.contains("W")) {
                throw CronParseException(
                    expression,
                    dialect,
                    "Special character 'W' (weekday) is not supported in Cron V1"
                )
            }
        }
    }

    private fun parseField(
        expression: String,
        dialect: CronDialect,
        token: String,
        fieldType: CronFieldType
    ): BitSet {
        val bitSet = BitSet(fieldType.max + 1)
        val parts = token.split(",")

        if (parts.isEmpty() || parts.any { it.isEmpty() }) {
            throw CronParseException(
                expression,
                dialect,
                "Invalid empty segment in field ${fieldType.fieldName}: '$token'"
            )
        }

        for (part in parts) {
            if (part == "*" || part == "?") {
                bitSet.set(fieldType.min, fieldType.max + 1)
                continue
            }

            if (part.contains("/")) {
                val slashParts = part.split("/")
                if (slashParts.size != 2 || slashParts[0].isEmpty() || slashParts[1].isEmpty()) {
                    throw CronParseException(
                        expression,
                        dialect,
                        "Invalid step expression '$part' in field ${fieldType.fieldName}"
                    )
                }

                val step = slashParts[1].toIntOrNull()
                    ?: throw CronParseException(
                        expression,
                        dialect,
                        "Step value '${slashParts[1]}' must be an integer in field ${fieldType.fieldName}"
                    )

                if (step <= 0) {
                    throw CronParseException(
                        expression,
                        dialect,
                        "Step value $step must be strictly positive in field ${fieldType.fieldName}"
                    )
                }

                val rangeStr = slashParts[0]
                val (rangeStart, rangeEnd) = if (rangeStr == "*") {
                    fieldType.min to fieldType.max
                } else if (rangeStr.contains("-")) {
                    parseRange(expression, dialect, rangeStr, fieldType)
                } else {
                    val startVal = parseSingleValue(expression, dialect, rangeStr, fieldType)
                    startVal to fieldType.max
                }

                var v = rangeStart
                while (v <= rangeEnd) {
                    bitSet.set(v)
                    if (step > rangeEnd - v) break
                    v += step
                }
            } else if (part.contains("-")) {
                val (rangeStart, rangeEnd) = parseRange(expression, dialect, part, fieldType)
                bitSet.set(rangeStart, rangeEnd + 1)
            } else {
                val value = parseSingleValue(expression, dialect, part, fieldType)
                bitSet.set(value)
            }
        }

        if (bitSet.isEmpty) {
            throw CronParseException(
                expression,
                dialect,
                "Field ${fieldType.fieldName} evaluated to an empty set for token '$token'"
            )
        }

        return bitSet
    }

    private fun parseRange(
        expression: String,
        dialect: CronDialect,
        token: String,
        fieldType: CronFieldType
    ): Pair<Int, Int> {
        val dashParts = token.split("-")
        if (dashParts.size != 2 || dashParts[0].isEmpty() || dashParts[1].isEmpty()) {
            throw CronParseException(
                expression,
                dialect,
                "Invalid range expression '$token' in field ${fieldType.fieldName}"
            )
        }

        val start = parseSingleValue(expression, dialect, dashParts[0], fieldType)
        val end = parseSingleValue(expression, dialect, dashParts[1], fieldType)

        if (start > end) {
            throw CronParseException(
                expression,
                dialect,
                "Range start $start must not be greater than range end $end in field ${fieldType.fieldName}"
            )
        }

        return start to end
    }

    private fun parseSingleValue(
        expression: String,
        dialect: CronDialect,
        token: String,
        fieldType: CronFieldType
    ): Int {
        val upper = token.uppercase(Locale.ROOT)

        val value = when (fieldType) {
            CronFieldType.MONTH -> {
                MONTH_NAMES[upper] ?: token.toIntOrNull()
            }
            CronFieldType.DAY_OF_WEEK -> {
                QUARTZ_DOW_NAMES[upper] ?: token.toIntOrNull()
            }
            else -> {
                token.toIntOrNull()
            }
        } ?: throw CronParseException(
            expression,
            dialect,
            "Invalid value '$token' in field ${fieldType.fieldName}"
        )

        if (value < fieldType.min || value > fieldType.max) {
            throw CronParseException(
                expression,
                dialect,
                "Value $value is out of range [${fieldType.min}..${fieldType.max}] for field ${fieldType.fieldName}"
            )
        }

        return value
    }

    private fun parseUnixDowField(expression: String, token: String): BitSet {
        val bitSet = BitSet(8)
        val parts = token.split(",")

        if (parts.isEmpty() || parts.any { it.isEmpty() }) {
            throw CronParseException(
                expression,
                CronDialect.UNIX,
                "Invalid empty segment in day-of-week: '$token'"
            )
        }

        for (part in parts) {
            if (part == "*") {
                // In Quartz DOW: 1..7
                bitSet.set(1, 8)
                continue
            }

            if (part.contains("/")) {
                val slashParts = part.split("/")
                if (slashParts.size != 2 || slashParts[0].isEmpty() || slashParts[1].isEmpty()) {
                    throw CronParseException(
                        expression,
                        CronDialect.UNIX,
                        "Invalid step expression '$part' in day-of-week"
                    )
                }

                val step = slashParts[1].toIntOrNull()
                    ?: throw CronParseException(
                        expression,
                        CronDialect.UNIX,
                        "Step value '${slashParts[1]}' must be an integer in day-of-week"
                    )

                if (step <= 0) {
                    throw CronParseException(
                        expression,
                        CronDialect.UNIX,
                        "Step value $step must be strictly positive in day-of-week"
                    )
                }

                val rangeStr = slashParts[0]
                val (rangeStart, rangeEnd) = if (rangeStr == "*") {
                    0 to 6
                } else if (rangeStr.contains("-")) {
                    parseUnixDowRange(expression, rangeStr)
                } else {
                    val startVal = parseUnixDowSingleValue(expression, rangeStr)
                    startVal to 6
                }

                var v = rangeStart
                while (v <= rangeEnd) {
                    val quartzDow = unixDowToQuartz(v)
                    bitSet.set(quartzDow)
                    if (step > rangeEnd - v) break
                    v += step
                }
            } else if (part.contains("-")) {
                val (rangeStart, rangeEnd) = parseUnixDowRange(expression, part)
                for (v in rangeStart..rangeEnd) {
                    val quartzDow = unixDowToQuartz(v)
                    bitSet.set(quartzDow)
                }
            } else {
                val unixVal = parseUnixDowSingleValue(expression, part)
                val quartzDow = unixDowToQuartz(unixVal)
                bitSet.set(quartzDow)
            }
        }

        if (bitSet.isEmpty) {
            throw CronParseException(
                expression,
                CronDialect.UNIX,
                "Field day-of-week evaluated to an empty set for token '$token'"
            )
        }

        return bitSet
    }

    private fun parseUnixDowRange(expression: String, token: String): Pair<Int, Int> {
        val dashParts = token.split("-")
        if (dashParts.size != 2 || dashParts[0].isEmpty() || dashParts[1].isEmpty()) {
            throw CronParseException(
                expression,
                CronDialect.UNIX,
                "Invalid range expression '$token' in day-of-week"
            )
        }

        val start = parseUnixDowSingleValue(expression, dashParts[0])
        val end = parseUnixDowSingleValue(expression, dashParts[1])

        if (start > end) {
            throw CronParseException(
                expression,
                CronDialect.UNIX,
                "Range start $start must not be greater than range end $end in day-of-week"
            )
        }

        return start to end
    }

    private fun parseUnixDowSingleValue(expression: String, token: String): Int {
        val upper = token.uppercase(Locale.ROOT)
        val value = UNIX_DOW_NAMES[upper] ?: token.toIntOrNull()
            ?: throw CronParseException(
                expression,
                CronDialect.UNIX,
                "Invalid value '$token' in day-of-week"
            )

        if (value < 0 || value > 7) {
            throw CronParseException(
                expression,
                CronDialect.UNIX,
                "Value $value is out of range [0..7] for day-of-week in UNIX dialect"
            )
        }

        return value
    }

    private fun unixDowToQuartz(unixDow: Int): Int = when (unixDow) {
        0, 7 -> 1 // Sunday
        1 -> 2    // Monday
        2 -> 3    // Tuesday
        3 -> 4    // Wednesday
        4 -> 5    // Thursday
        5 -> 6    // Friday
        6 -> 7    // Saturday
        else -> throw IllegalArgumentException("Invalid unix DOW: $unixDow")
    }
}

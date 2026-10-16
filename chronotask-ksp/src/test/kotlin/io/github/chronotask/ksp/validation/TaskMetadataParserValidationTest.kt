package io.github.chronotask.ksp.validation

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSNode
import io.github.chronotask.cron.CronDialect
import io.github.chronotask.cron.CronParseException
import io.github.chronotask.cron.CronParser
import io.github.chronotask.ksp.TaskMetadataParser
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.DateTimeException
import java.time.ZoneId

class RecordingKspLogger : KSPLogger {
    val errorMessages = mutableListOf<String>()
    val warnMessages = mutableListOf<String>()

    override fun error(message: String, symbol: KSNode?) {
        errorMessages.add(message)
    }

    override fun warn(message: String, symbol: KSNode?) {
        warnMessages.add(message)
    }

    override fun info(message: String, symbol: KSNode?) {}
    override fun logging(message: String, symbol: KSNode?) {}
    override fun exception(e: Throwable) {
        errorMessages.add(e.message ?: "exception")
    }
}

class TaskMetadataParserValidationTest {

    @Test
    fun `timezone validation rejects invalid zone strings`() {
        assertThrows(DateTimeException::class.java) {
            ZoneId.of("Invalid/Timezone")
        }
    }

    @Test
    fun `cron validation rejects invalid syntax`() {
        assertThrows(CronParseException::class.java) {
            CronParser.parse("invalid-cron", CronDialect.QUARTZ)
        }
    }

    @Test
    fun `cron validation accepts valid Quartz and UNIX expressions`() {
        val quartz = CronParser.parse("0 0 12 * * ?", CronDialect.QUARTZ)
        assertTrue(quartz.expression == "0 0 12 * * ?")

        val unix = CronParser.parse("0 12 * * *", CronDialect.UNIX)
        assertTrue(unix.expression == "0 12 * * *")
    }
}

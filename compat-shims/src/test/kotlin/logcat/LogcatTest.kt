package logcat

import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class LogcatTest {
    @Test
    fun `asLog includes the stack trace`() {
        IllegalStateException("boom").asLog() shouldContain "IllegalStateException: boom"
    }

    @Test
    fun `logging never throws`() {
        logcat(LogPriority.ERROR) { "message" }
        logcat("tag", LogPriority.DEBUG) { "message" }
    }
}

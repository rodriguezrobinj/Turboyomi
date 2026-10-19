package mihon.desktop.harness

import eu.kanade.tachiyomi.network.HttpException
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test

class ReportTest {
    @Test
    fun `blocked extensions are left out of the pass rate`() {
        val report = Report(
            listOf(
                ExtensionResult("a", Outcome.PASS, ""),
                ExtensionResult("b", Outcome.PASS, ""),
                ExtensionResult("c", Outcome.ERROR, "x"),
                ExtensionResult("d", Outcome.BLOCKED, "cloudflare"),
            ),
        )
        report.passRate shouldBe 2.0 / 3.0
        report.toMarkdown() shouldContain "66.7%"
    }

    @Test
    fun `empty report fails`() {
        Report(emptyList()).passRate shouldBe 0.0
    }

    @Test
    fun `classifies the failures the threshold depends on`() {
        OutcomeClassifier.classify(HttpException(403)) shouldBe Outcome.BLOCKED
        OutcomeClassifier.classify(HttpException(503)) shouldBe Outcome.BLOCKED
        OutcomeClassifier.classify(IllegalStateException("Failed to bypass Cloudflare")) shouldBe Outcome.BLOCKED
        OutcomeClassifier.classify(NoClassDefFoundError("android/graphics/Picture")) shouldBe
            Outcome.MISSING_ANDROID_API
        OutcomeClassifier.classify(RuntimeException("x", RuntimeException("Stub!"))) shouldBe
            Outcome.MISSING_ANDROID_API
        OutcomeClassifier.classify(HttpException(500)) shouldBe Outcome.ERROR
        val timeout = runCatching { runBlocking { withTimeout(1) { delay(1_000) } } }.exceptionOrNull()
        (timeout is TimeoutCancellationException) shouldBe true
        OutcomeClassifier.classify(timeout!!) shouldBe Outcome.TIMEOUT
    }
}

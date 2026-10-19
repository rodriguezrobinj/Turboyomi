package mihon.desktop.harness

import eu.kanade.tachiyomi.network.HttpException
import kotlinx.coroutines.TimeoutCancellationException
import java.util.Locale

enum class Outcome { PASS, BLOCKED, INSTALL_FAILED, LOAD_FAILED, MISSING_ANDROID_API, TIMEOUT, ERROR }

data class ExtensionResult(val pkgName: String, val outcome: Outcome, val detail: String)

data class Report(val results: List<ExtensionResult>) {
    /** BLOCKED (Cloudflare) is left out until the desktop WebView exists (subproject 2). */
    private val considered get() = results.filter { it.outcome != Outcome.BLOCKED }

    val passRate: Double
        get() = if (considered.isEmpty()) {
            0.0
        } else {
            considered.count { it.outcome == Outcome.PASS }.toDouble() /
                considered.size
        }

    fun toMarkdown(): String = buildString {
        val passed = considered.count { it.outcome == Outcome.PASS }
        val rate = "%.1f".format(Locale.ROOT, passRate * 100)
        appendLine("# Extension compatibility")
        appendLine()
        appendLine(
            "Pass rate: **$rate%** ($passed/${considered.size}, ${results.size - considered.size} blocked excluded)",
        )
        appendLine()
        Outcome.entries.forEach { outcome -> appendLine("- $outcome: ${results.count { it.outcome == outcome }}") }
        appendLine()
        appendLine("| Package | Outcome | Detail |")
        appendLine("|---|---|---|")
        results.forEach {
            val detail = it.detail.lineSequence().first().take(120).replace("|", "\\|")
            appendLine("| ${it.pkgName} | ${it.outcome} | $detail |")
        }
    }
}

object OutcomeClassifier {
    fun classify(error: Throwable): Outcome {
        val chain = generateSequence(error) { it.cause }.toList()
        return when {
            chain.any { it is TimeoutCancellationException || it is ProbeTimeout } -> Outcome.TIMEOUT
            chain.any {
                it is NoClassDefFoundError || it is AbstractMethodError || it.message == "Stub!"
            } -> Outcome.MISSING_ANDROID_API
            chain.any { it is HttpException && it.code in setOf(403, 503) } -> Outcome.BLOCKED
            chain.any { it.message?.contains("cloudflare", ignoreCase = true) == true } -> Outcome.BLOCKED
            else -> Outcome.ERROR
        }
    }
}

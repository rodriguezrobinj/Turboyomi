package mihon.desktop.harness

import eu.kanade.tachiyomi.extension.model.Extension
import kotlinx.coroutines.runBlocking
import mihon.desktop.core.AppDirs
import mihon.desktop.core.Platform
import kotlin.io.path.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.writeText
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

private fun Array<String>.option(name: String): String? = indexOf(name).takeIf { it >= 0 }?.let { getOrNull(it + 1) }

fun main(args: Array<String>) {
    val repo = args.option("--repo") ?: run {
        System.err.println("Usage: --repo <index url> [--limit 100] [--threshold 0.8] [--out compat-report.md] [--packages file]")
        exitProcess(2)
    }
    val limit = args.option("--limit")?.toInt() ?: 100
    val threshold = args.option("--threshold")?.toDouble() ?: 0.8
    val out = Path(args.option("--out") ?: "compat-report.md")
    val packages = (args.option("--packages") ?: "compat-harness/extensions.txt").let(::Path)
        .takeIf { it.exists() }
        ?.readLines()?.map { it.trim() }?.filter { it.isNotEmpty() && !it.startsWith("#") }

    val home = createTempDirectory("compat-harness")
    val graph = Platform.start(AppDirs(home.resolve("data"), home.resolve("local")))
    val manager = graph.extensionManager

    val report = runBlocking {
        manager.addStore(repo).getOrThrow()
        val targets = selectPackages(manager.available(), packages, limit)
        Report(
            targets.map { available ->
                val installed = runCatching { manager.install(available) }.getOrElse {
                    return@map ExtensionResult(available.pkgName, Outcome.INSTALL_FAILED, it.toString())
                }
                if (installed !is Extension.Loaded) {
                    val reason = (installed as Extension.NotLoaded).reason
                    val outcome = (reason as? Extension.NotLoaded.Reason.Failed)
                        ?.let { if ("NoClassDefFoundError" in it.stackTrace || "Stub!" in it.stackTrace) Outcome.MISSING_ANDROID_API else null }
                        ?: Outcome.LOAD_FAILED
                    return@map ExtensionResult(available.pkgName, outcome, reason.toString())
                }
                val errors = installed.sources.map { probe(it, 30.seconds) }
                if (errors.any { it == null }) {
                    ExtensionResult(available.pkgName, Outcome.PASS, "")
                } else {
                    val first = errors.filterNotNull().first()
                    ExtensionResult(available.pkgName, OutcomeClassifier.classify(first), first.toString())
                }
            },
        )
    }

    out.writeText(report.toMarkdown())
    println(report.toMarkdown())
    exitProcess(if (report.passRate >= threshold) 0 else 1)
}

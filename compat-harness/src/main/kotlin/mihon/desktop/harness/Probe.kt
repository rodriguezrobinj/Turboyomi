package mihon.desktop.harness

import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

class ProbeTimeout(timeout: Duration) : Exception("No answer within $timeout")

// Detached from the caller: a source that blocks its thread must not hold the harness past the timeout.
// ponytail: a source that ignores interrupts leaks its thread until exitProcess; fine for a one-shot CLI
private val probeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** Null when the source answered its first popular page in time, otherwise what went wrong. */
suspend fun probe(source: Source, timeout: Duration): Throwable? {
    val work = probeScope.async {
        runInterruptible { runBlocking { runCatching { source.getPopularManga(1) } } }
    }
    val result = withTimeoutOrNull(timeout) { work.await() }
    if (result == null) {
        work.cancel()
        return ProbeTimeout(timeout)
    }
    return result.exceptionOrNull()
}

fun selectPackages(
    available: List<Extension.Available>,
    packages: List<String>?,
    limit: Int,
): List<Extension.Available> {
    if (packages != null) {
        val byPkg = available.associateBy { it.pkgName }
        return packages.mapNotNull { byPkg[it] }.take(limit)
    }
    return available.filter { it.lang == "all" || it.lang == "en" }.sortedBy { it.pkgName }.take(limit)
}

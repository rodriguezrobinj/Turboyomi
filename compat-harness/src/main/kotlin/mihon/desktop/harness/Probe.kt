package mihon.desktop.harness

import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration

/** Null when the source answered its first popular page in time, otherwise what went wrong. */
suspend fun probe(source: Source, timeout: Duration): Throwable? = try {
    withTimeout(timeout) { source.getPopularManga(1) }
    null
} catch (e: TimeoutCancellationException) {
    e
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    e
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

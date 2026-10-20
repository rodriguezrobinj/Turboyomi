// Null implementation until subproject 4; replace with the Mihon port.
package eu.kanade.tachiyomi.data.download

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Suppress("UNUSED_PARAMETER")
@Inject
@SingleIn(AppScope::class)
class DownloadCache {
    val changes: Flow<Unit> = flowOf(Unit)

    fun isChapterDownloaded(
        chapterName: String,
        chapterScanlator: String?,
        chapterUrl: String,
        mangaTitle: String,
        sourceId: Long,
    ): Boolean = false
}

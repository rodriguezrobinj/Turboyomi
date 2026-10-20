// Null implementation until subproject 4; replace with the Mihon port.
package eu.kanade.tachiyomi.data.download

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.domain.chapter.model.Chapter

@Suppress("UNUSED_PARAMETER")
@Inject
@SingleIn(AppScope::class)
class DownloadProvider {
    fun isChapterDirNameChanged(oldChapter: Chapter, newChapter: Chapter): Boolean = false
}

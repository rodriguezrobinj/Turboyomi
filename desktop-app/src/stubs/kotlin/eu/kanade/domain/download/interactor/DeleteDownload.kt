// Null implementation until subproject 4; replace with the Mihon port.
package eu.kanade.domain.download.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga

@Suppress("UNUSED_PARAMETER")
@Inject
class DeleteDownload {
    suspend fun awaitAll(manga: Manga, vararg chapters: Chapter) {}
}

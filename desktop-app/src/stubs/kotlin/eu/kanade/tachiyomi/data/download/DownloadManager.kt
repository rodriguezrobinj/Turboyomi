// Null implementation until subproject 4; replace with the Mihon port.
package eu.kanade.tachiyomi.data.download

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga

@Suppress("UNUSED_PARAMETER", "RedundantSuspendModifier")
@Inject
@SingleIn(AppScope::class)
class DownloadManager {
    val queueState: StateFlow<List<Download>> = MutableStateFlow(emptyList())

    fun statusFlow(): Flow<Download> = emptyFlow()

    fun progressFlow(): Flow<Download> = emptyFlow()

    fun getDownloadCount(manga: Manga): Int = 0

    /** Every manga with 0: callers read it with getValue. */
    fun getDownloadCounts(manga: List<Manga>): Map<Long, Int> = manga.associate { it.id to 0 }

    fun getDownloadedChapterIds(chapters: List<Chapter>, manga: Manga): Set<Long> = emptySet()

    fun getQueuedDownloadOrNull(chapterId: Long): Download? = null

    fun getQueuedDownloadsByChapterId(): Map<Long, Download> = emptyMap()

    fun isChapterDownloaded(
        chapterName: String,
        chapterScanlator: String?,
        chapterUrl: String,
        mangaTitle: String,
        sourceId: Long,
    ): Boolean = false

    fun startDownloads() {}

    suspend fun startDownloadNow(chapterId: Long) {}

    suspend fun downloadChapters(manga: Manga, chapters: List<Chapter>, autoStart: Boolean = true) {}

    suspend fun cancelQueuedDownloads(downloads: List<Download>) {}

    suspend fun deleteChapters(chapters: List<Chapter>, manga: Manga, source: Source) {}

    suspend fun deleteManga(manga: Manga, source: Source, removeQueued: Boolean = true) {}

    suspend fun renameManga(manga: Manga, newTitle: String) {}

    suspend fun renameChapter(source: Source, manga: Manga, oldChapter: Chapter, newChapter: Chapter) {}
}

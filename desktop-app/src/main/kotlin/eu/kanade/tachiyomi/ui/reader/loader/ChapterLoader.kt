package eu.kanade.tachiyomi.ui.reader.loader

import android.content.Context
import eu.kanade.domain.chapter.model.toSChapter
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.core.archive.archiveReader
import mihon.core.archive.epubReader
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.model.StubSource
import tachiyomi.i18n.MR
import tachiyomi.source.local.LocalSource
import tachiyomi.source.local.io.Format

/**
 * Loader used to retrieve the [PageLoader] for a given chapter.
 *
 * desktop: Mihon's loaders for downloaded, local and online chapters.
 */
class ChapterLoader(
    private val context: Context,
    private val chapterCache: ChapterCache,
    private val appScope: CoroutineScope,
    private val pagesAhead: Int,
    private val manga: Manga,
    private val source: Source,
    private val downloadManager: DownloadManager,
    private val downloadProvider: DownloadProvider,
) {

    /** Assigns the chapter's page loader and loads its pages. Returns immediately if it's already loaded. */
    suspend fun loadChapter(chapter: ReaderChapter) {
        if (chapter.state is ReaderChapter.State.Loaded && chapter.pageLoader != null) return

        chapter.state = ReaderChapter.State.Loading
        withContext(Dispatchers.IO) {
            logcat { "Loading pages for ${chapter.chapter.name} of ${manga.title}" }
            try {
                val loader = getPageLoader(chapter)
                chapter.pageLoader = loader

                val pages = loader.getPages().onEach { it.chapter = chapter }
                if (pages.isEmpty()) throw Exception(context.stringResource(MR.strings.page_list_empty_error))

                // If the chapter is partially read, start at the last page the user read
                if (!chapter.chapter.read) chapter.requestedPage = chapter.chapter.lastPageRead

                chapter.state = ReaderChapter.State.Loaded(pages)
            } catch (e: Throwable) {
                chapter.state = ReaderChapter.State.Error(e)
                throw e
            }
        }
    }

    private suspend fun getPageLoader(chapter: ReaderChapter): PageLoader {
        val dbChapter = chapter.chapter
        val isDownloaded = downloadManager.isChapterDownloadedOnDisk(
            dbChapter.name,
            dbChapter.scanlator,
            dbChapter.url,
            manga.title,
            source,
        )
        return when {
            isDownloaded -> DownloadPageLoader(chapter, manga, source, downloadManager, downloadProvider)
            source is LocalSource -> when (val format = source.getFormat(chapter.chapter.toSChapter())) {
                is Format.Directory -> DirectoryPageLoader(format.file)
                is Format.Archive -> ArchivePageLoader(format.file.archiveReader(context))
                is Format.Epub -> EpubPageLoader(format.file.epubReader(context))
            }
            source is HttpSource -> HttpPageLoader(chapter, source, chapterCache, appScope, pagesAhead)
            source is StubSource -> error(context.stringResource(MR.strings.source_not_installed, source.toString()))
            else -> error(context.stringResource(MR.strings.loader_not_implemented_error))
        }
    }
}

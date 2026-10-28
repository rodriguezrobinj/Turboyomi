package eu.kanade.tachiyomi.ui.reader

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.domain.chapter.model.toSChapter
import eu.kanade.domain.manga.interactor.SetMangaViewerFlags
import eu.kanade.domain.manga.model.downloadedFilter
import eu.kanade.domain.manga.model.readingMode
import eu.kanade.domain.source.interactor.GetIncognitoState
import eu.kanade.domain.track.interactor.TrackChapter
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.reader.loader.ChapterLoader
import eu.kanade.tachiyomi.ui.reader.loader.DownloadPageLoader
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.util.chapter.removeDuplicates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.core.metro.AppCoroutineScope
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.service.getChapterSort
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.history.interactor.GetNextChapters
import tachiyomi.domain.history.interactor.UpsertHistory
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.source.local.isLocal
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * desktop: the subset of Mihon's ReaderViewModel the desktop reader needs (spec 2a §4): loading, progress, history,
 * adjacent chapters, bookmarks and the per-manga reading mode. No downloads, trackers, orientation, crop borders,
 * colour filters, set-as-cover or Android's saved state; the reader screen passes the manga and chapter ids.
 */
@AssistedInject
class ReaderViewModel(
    // desktop: Metro matches assisted parameters by name; it has no identifiers
    @Assisted val mangaId: Long,
    @Assisted initialChapterId: Long,
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val context: Context,
    private val sourceManager: SourceManager,
    private val chapterCache: ChapterCache,
    val readerPreferences: ReaderPreferences,
    private val getManga: GetManga,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val upsertHistory: UpsertHistory,
    private val updateChapter: UpdateChapter,
    private val setMangaViewerFlags: SetMangaViewerFlags,
    private val getIncognitoState: GetIncognitoState,
    private val libraryPreferences: LibraryPreferences,
    private val downloadManager: DownloadManager,
    private val downloadProvider: DownloadProvider,
    private val downloadPreferences: DownloadPreferences,
    private val getNextChapters: GetNextChapters,
    private val trackChapter: TrackChapter,
    private val trackPreferences: TrackPreferences,
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(mangaId: Long, initialChapterId: Long): ReaderViewModel
    }

    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()

    val manga: Manga?
        get() = state.value.manga

    private var chapterId = initialChapterId
    private var loader: ChapterLoader? = null
    private var chapterReadStartTime: Instant? = null
    private var incognitoMode = false
    private var chapterList: List<ReaderChapter> = emptyList()

    // Mihon: how many chapters to queue ahead while reading downloaded ones
    private val downloadAheadAmount = downloadPreferences.autoDownloadWhileReading.get()
    private var unfilteredChapterList: List<Chapter>? = null

    // Serializes the progress writes, which outlive the reader, so they are applied in order
    private val progressWrites = Mutex()

    @Volatile
    private var pendingProgressChapter: ReaderChapter? = null
    private var pendingProgressSave: Job? = null

    // The next chapter's background load, which a move to that chapter waits for instead of loading it twice
    private var preloadJob: Job? = null
    private var preloadChapter: ReaderChapter? = null

    init {
        viewModelScope.launch { init() }
    }

    override fun onCleared() {
        state.value.viewerChapters?.unref()
    }

    private suspend fun init() {
        withContext(Dispatchers.IO) {
            try {
                val manga = getManga.await(mangaId) ?: error("Requested manga of id $mangaId not found")
                val source = sourceManager.getOrStub(manga.source)
                incognitoMode = getIncognitoState.await(manga.source)
                mutableState.update { it.copy(manga = manga, source = source, initError = null) }

                val loader = ChapterLoader(
                    context,
                    chapterCache,
                    appScope,
                    readerPreferences.preloadPages.get(),
                    manga,
                    source,
                    downloadManager,
                    downloadProvider,
                )
                this@ReaderViewModel.loader = loader
                chapterList = loadChapterList(manga)
                restartReadTimer()
                loadChapter(loader, chapterList.first { it.chapter.id == chapterId })
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                logcat(LogPriority.ERROR, e)
                mutableState.update { it.copy(initError = e) }
            }
        }
    }

    /** After a failed start, for example a page list that didn't load: starts over. */
    fun retryInit() {
        mutableState.update { it.copy(initError = null) }
        viewModelScope.launch { init() }
    }

    private suspend fun getUnfilteredChapterList(manga: Manga): List<Chapter> =
        unfilteredChapterList ?: getChaptersByMangaId.await(manga.id, applyScanlatorFilter = false)
            .also { unfilteredChapterList = it }

    private suspend fun loadChapterList(manga: Manga): List<ReaderChapter> {
        val chapters = getChaptersByMangaId.await(manga.id, applyScanlatorFilter = true).let { filtered ->
            if (filtered.any { it.id == chapterId }) {
                filtered
            } else {
                filtered + getUnfilteredChapterList(manga).filter { it.id == chapterId }
            }
        }
        val selectedChapter = chapters.find { it.id == chapterId }
            ?: error("Requested chapter of id $chapterId not found in chapter list")

        val skipRead = readerPreferences.skipRead.get()
        val skipFiltered = readerPreferences.skipFiltered.get()
        val chaptersForReader = if (!skipRead && !skipFiltered) {
            chapters
        } else {
            chapters.filterNot { (skipRead && it.read) || (skipFiltered && isFilteredOut(manga, it)) }
                .let { if (it.any { c -> c.id == chapterId }) it else it + selectedChapter }
        }

        return chaptersForReader
            .sortedWith(getChapterSort(manga, sortDescending = false))
            .let { if (readerPreferences.skipDupe.get()) it.removeDuplicates(selectedChapter) else it }
            .map(::ReaderChapter)
    }

    // desktop: Mihon's chapter filters, as a function of the desktop reader's chapter list
    private fun isFilteredOut(manga: Manga, chapter: Chapter): Boolean =
        (manga.unreadFilterRaw == Manga.CHAPTER_SHOW_READ && !chapter.read) ||
            (manga.unreadFilterRaw == Manga.CHAPTER_SHOW_UNREAD && chapter.read) ||
            (manga.bookmarkedFilterRaw == Manga.CHAPTER_SHOW_BOOKMARKED && !chapter.bookmark) ||
            (manga.bookmarkedFilterRaw == Manga.CHAPTER_SHOW_NOT_BOOKMARKED && chapter.bookmark) ||
            (manga.downloadedFilter != TriState.DISABLED && isDownloadedFilteredOut(manga, chapter))

    private fun isDownloadedFilteredOut(manga: Manga, chapter: Chapter): Boolean {
        val downloaded = manga.isLocal() ||
            downloadManager.isChapterDownloaded(chapter.name, chapter.scanlator, chapter.url, manga.title, manga.source)
        return when (manga.downloadedFilter) {
            TriState.ENABLED_IS -> !downloaded
            TriState.ENABLED_NOT -> downloaded
            TriState.DISABLED -> false
        }
    }

    /** Loads [chapter] with [loader] and makes it the active one. Callers handle errors. */
    private suspend fun loadChapter(
        loader: ChapterLoader,
        chapter: ReaderChapter,
        jump: Boolean = false,
    ): ViewerChapters {
        // A move elsewhere cancels it: a slow source would hold the move up to the call timeout
        if (chapter == preloadChapter) preloadJob?.join() else preloadJob?.cancelAndJoin()
        loader.loadChapter(chapter)

        val chapterPos = chapterList.indexOf(chapter)
        val newChapters = ViewerChapters(
            chapter,
            chapterList.getOrNull(chapterPos - 1),
            chapterList.getOrNull(chapterPos + 1),
        )
        withContext(Dispatchers.Main) {
            mutableState.update {
                // Add new references first to avoid unnecessary recycling
                newChapters.ref()
                it.viewerChapters?.unref()
                it.copy(
                    viewerChapters = newChapters,
                    bookmarked = chapter.chapter.bookmark,
                    chapterJumps = if (jump) it.chapterJumps + 1 else it.chapterJumps,
                )
            }
        }
        chapterId = chapter.chapter.id
        return newChapters
    }

    /** The viewer settled on a page: saves the progress and, if it belongs to another chapter, makes that one active. */
    fun onPageSelected(page: ReaderPage) {
        // InsertPage doesn't change page progress
        if (page is InsertPage) return

        val selectedChapter = page.chapter
        if (selectedChapter.pages == null) return

        updateChapterProgress(selectedChapter, page)

        if (selectedChapter != state.value.currentChapter) {
            logcat { "Setting ${selectedChapter.chapter.url} as active" }
            loadNewChapter(selectedChapter)
        }

        // Mihon: once a quarter of the chapter is read, the next downloaded chapters are queued ahead
        val pages = selectedChapter.pages ?: return
        if (page.number.toDouble() / pages.size > 0.25) downloadNextChapters()

        // Mihon: near the end the next chapter loads, so the viewer shows its first pages after this one's last
        if (pages.size - page.number < PRELOAD_RANGE && selectedChapter == state.value.currentChapter) {
            state.value.viewerChapters?.nextChapter?.let(::preload)
        }
    }

    private fun preload(chapter: ReaderChapter) {
        val loader = loader ?: return
        if (chapter.state != ReaderChapter.State.Wait || preloadJob?.isActive == true) return
        logcat { "Preloading ${chapter.chapter.url}" }
        preloadChapter = chapter
        preloadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                loader.loadChapter(chapter)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                // Left in its error state: moving on to the chapter loads it again and shows the error there
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    // desktop: Mihon's, ported for downloads (spec 4b §3 C)
    private fun downloadNextChapters() {
        if (downloadAheadAmount == 0) return
        val manga = manga ?: return

        // Only download ahead if current + next chapter is already downloaded too to avoid jank
        if (state.value.currentChapter?.pageLoader !is DownloadPageLoader) return
        val nextChapter = state.value.viewerChapters?.nextChapter?.chapter ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val isNextChapterDownloaded = downloadManager.isChapterDownloaded(
                nextChapter.name,
                nextChapter.scanlator,
                nextChapter.url,
                manga.title,
                manga.source,
            )
            if (!isNextChapterDownloaded) return@launch

            val chaptersToDownload = getNextChapters.await(manga.id, nextChapter.id).run {
                if (readerPreferences.skipDupe.get()) {
                    removeDuplicates(nextChapter)
                } else {
                    this
                }
            }.take(downloadAheadAmount)

            downloadManager.downloadChapters(
                manga,
                chaptersToDownload,
            )
        }
    }

    // desktop: Mihon's, ported for downloads (spec 4b §3 C)
    private suspend fun deleteChapterIfNeeded(currentChapter: ReaderChapter) {
        val removeAfterReadSlots = downloadPreferences.removeAfterReadSlots.get()
        if (removeAfterReadSlots == -1) return
        val manga = manga ?: return

        // Determine which chapter should be deleted and enqueue
        val currentChapterPosition = chapterList.indexOf(currentChapter)
        val chapterToDelete = chapterList.getOrNull(currentChapterPosition - removeAfterReadSlots)

        if (chapterToDelete == null || !chapterToDelete.chapter.read) return

        val markDuplicateAsRead = libraryPreferences.markDuplicateReadChapterAsRead.get()
            .contains(LibraryPreferences.MARK_DUPLICATE_CHAPTER_READ_EXISTING)
        val duplicateChapters = if (markDuplicateAsRead) {
            getUnfilteredChapterList(manga)
                .filter {
                    it.id != chapterToDelete.chapter.id &&
                        it.isRecognizedNumber &&
                        it.chapterNumber == chapterToDelete.chapter.chapterNumber
                }
                // The list is loaded once, before these were marked read along with chapterToDelete
                .map { it.copy(read = true) }
        } else {
            emptyList()
        }
        enqueueDeleteChapters(listOf(chapterToDelete.chapter) + duplicateChapters)
    }

    // desktop: Mihon's, ported for downloads (spec 4b §3 C)
    private suspend fun enqueueDeleteChapters(chapters: List<Chapter>) {
        val manga = manga ?: return
        downloadManager.enqueueChaptersToDelete(chapters, manga)
    }

    // desktop: Mihon's, ported for downloads (spec 4b §3 C)
    private fun deletePendingChapters() {
        appScope.launch {
            // Progress writes started before leaving may still enqueue the chapter just finished
            progressWrites.withLock {}
            downloadManager.deletePendingChapters()
        }
    }

    private fun loadNewChapter(chapter: ReaderChapter) {
        val loader = loader ?: return
        viewModelScope.launch(Dispatchers.IO) {
            updateHistory()
            restartReadTimer()
            try {
                loadChapter(loader, chapter)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    private suspend fun loadAdjacent(chapter: ReaderChapter, jump: Boolean) {
        val loader = loader ?: return
        mutableState.update { it.copy(isLoadingAdjacentChapter = true) }
        try {
            // Spec 2a §4: the history is updated when the chapter changes
            withContext(Dispatchers.Main.immediate) {
                saveChapterProgress()
                updateHistory()
                restartReadTimer()
            }
            withContext(Dispatchers.IO) { loadChapter(loader, chapter, jump) }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            logcat(LogPriority.ERROR, e)
        } finally {
            mutableState.update { it.copy(isLoadingAdjacentChapter = false) }
        }
    }

    /**
     * Moves to the next chapter. A viewer going on past the end passes the chapter it shows as [from]: a request it
     * makes after that chapter stopped being the current one (a wheel turn while the next one replaces it) would skip
     * a chapter, and the viewer stays where it is, on the way across. Without [from] (the chapter buttons and keys)
     * the viewer jumps to the chapter's page.
     */
    suspend fun loadNextChapter(from: ReaderChapter? = null) {
        if (from != null && from != state.value.currentChapter) return
        val nextChapter = state.value.viewerChapters?.nextChapter ?: return
        loadAdjacent(nextChapter, jump = from == null)
    }

    suspend fun loadPreviousChapter(from: ReaderChapter? = null) {
        if (from != null && from != state.value.currentChapter) return
        val prevChapter = state.value.viewerChapters?.prevChapter ?: return
        loadAdjacent(prevChapter, jump = from == null)
    }

    /** Downloads a page that failed again. */
    fun retryPage(page: ReaderPage) {
        page.chapter.pageLoader?.retryPage(page)
    }

    /**
     * Saves the last page read and whether the chapter is read, unless incognito mode is on. Completing a chapter is
     * saved right away; other page changes once the reader settles (spec 2a §4: grouped every 500 ms).
     */
    private fun updateChapterProgress(readerChapter: ReaderChapter, page: Page) {
        val pageIndex = page.index
        mutableState.update { it.copy(currentPage = pageIndex + 1) }
        readerChapter.requestedPage = pageIndex

        if (incognitoMode || page.status is Page.State.Error) return

        val read = readerChapter.chapter.read || readerChapter.pages?.lastIndex == pageIndex
        // A page change in another chapter would replace that chapter's pending save
        if (pendingProgressChapter != readerChapter) saveChapterProgress()
        readerChapter.update { it.copy(lastPageRead = pageIndex, read = read) }
        pendingProgressSave?.cancel()

        if (readerChapter.pages?.lastIndex == pageIndex) {
            pendingProgressChapter = null
            writeChapterProgress(readerChapter, onComplete = true)
        } else {
            pendingProgressChapter = readerChapter
            pendingProgressSave = viewModelScope.launch {
                delay(PROGRESS_SAVE_DELAY)
                saveChapterProgress()
            }
        }
    }

    private fun saveChapterProgress() {
        val readerChapter = pendingProgressChapter ?: return
        pendingProgressChapter = null
        writeChapterProgress(readerChapter, onComplete = false)
    }

    /** Writes the progress of [readerChapter] on the app scope, so it's saved even when the reader is closing. */
    // desktop: Mihon's, in the desktop reader's progress writes (spec 5b-1 §3 B, G)
    private fun updateTrackChapterRead(readerChapter: ReaderChapter) {
        if (incognitoMode) return
        if (!trackPreferences.autoUpdateTrack.get()) return
        val manga = manga ?: return

        // Reading the last page and leaving right away is common, so it outlives the reader
        appScope.launch {
            trackChapter.await(context, manga.id, readerChapter.chapter.chapterNumber)
        }
    }

    private fun writeChapterProgress(readerChapter: ReaderChapter, onComplete: Boolean) {
        // Immediate and through the mutex, which is fair, so the writes land in the order they were made
        appScope.launch(Dispatchers.Main.immediate) {
            progressWrites.withLock {
                if (onComplete) deleteChapterIfNeeded(readerChapter)
                if (onComplete) updateTrackChapterRead(readerChapter)
                if (onComplete) markDuplicatesRead(readerChapter)
                val chapter = readerChapter.chapter
                updateChapter.await(
                    ChapterUpdate(chapter.id) {
                        read = chapter.read
                        lastPageRead = chapter.lastPageRead
                    },
                )
            }
        }
    }

    private suspend fun markDuplicatesRead(readerChapter: ReaderChapter) {
        val markDuplicateAsRead = libraryPreferences.markDuplicateReadChapterAsRead.get()
            .contains(LibraryPreferences.MARK_DUPLICATE_CHAPTER_READ_EXISTING)
        val manga = manga ?: return
        if (!markDuplicateAsRead) return

        val duplicateUnreadChapters = getUnfilteredChapterList(manga).mapNotNull { chapter ->
            if (!chapter.read && chapter.isRecognizedNumber &&
                chapter.chapterNumber == readerChapter.chapter.chapterNumber
            ) {
                ChapterUpdate(chapter.id) { read = true }
            } else {
                null
            }
        }
        updateChapter.awaitAll(duplicateUnreadChapters)
    }

    fun restartReadTimer() {
        chapterReadStartTime = Clock.System.now()
    }

    private suspend fun updateHistory() {
        val readerChapter = state.value.currentChapter ?: return
        if (incognitoMode) return
        val endTime = Clock.System.now()
        val sessionReadDuration = chapterReadStartTime?.let { (endTime - it).inWholeMilliseconds } ?: 0
        chapterReadStartTime = null
        upsertHistory.await(HistoryUpdate(readerChapter.chapter.id, endTime, sessionReadDuration))
    }

    /** Writes the pending progress and the history now and waits for both, for when the window closes. */
    suspend fun flush() {
        withContext(Dispatchers.Main.immediate) {
            saveChapterProgress()
            // Fair mutex: this waits for every progress write launched before it
            progressWrites.withLock { }
            updateHistory()
        }
    }

    /** Leaving the reader: the history is saved on the app scope, which outlives the reader. */
    fun saveHistory() {
        appScope.launch { flush() }
        deletePendingChapters()
    }

    /** The URL of the chapter the reader is on or was opening, for "open in browser" when it failed to load. */
    fun getChapterUrl(): String? {
        val chapter = state.value.currentChapter?.chapter
            ?: chapterList.find { it.chapter.id == chapterId }?.chapter
            ?: return null
        val source = state.value.source as? HttpSource ?: return null
        return try {
            source.getChapterUrl(chapter.toSChapter())
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            null
        }
    }

    fun toggleChapterBookmark() {
        val readerChapter = state.value.currentChapter ?: return
        val bookmarked = !readerChapter.chapter.bookmark
        readerChapter.update { it.copy(bookmark = bookmarked) }
        // On Main so quick toggles are saved in order
        viewModelScope.launch {
            updateChapter.await(ChapterUpdate(readerChapter.chapter.id) { bookmark = bookmarked })
        }
        mutableState.update { it.copy(bookmarked = bookmarked) }
    }

    /** This manga's reading mode, or the default one from the settings. Never [ReadingMode.DEFAULT]. */
    fun getMangaReadingMode(): ReadingMode {
        val mangaMode = ReadingMode.fromPreference(manga?.readingMode?.toInt())
        if (mangaMode != ReadingMode.DEFAULT) return mangaMode
        return ReadingMode.fromPreference(readerPreferences.defaultReadingMode.get())
            .takeIf { it != ReadingMode.DEFAULT } ?: ReadingMode.RIGHT_TO_LEFT
    }

    private var readingModeJob: Job? = null

    fun setMangaReadingMode(readingMode: ReadingMode) {
        val manga = manga ?: return
        // Only the latest pick matters, and letting an older one finish last would restore its choice
        readingModeJob?.cancel()
        readingModeJob = viewModelScope.launch(Dispatchers.IO) {
            setMangaViewerFlags.awaitSetReadingMode(manga.id, readingMode.flagValue.toLong())
            val updatedManga = getManga.await(manga.id)
            // desktop: the reader screen rebuilds its viewer when the manga changes, at the chapter's requestedPage,
            // which updateChapterProgress keeps on the page being read (Mihon sends ReloadViewerChapters instead)
            mutableState.update { it.copy(manga = updatedManga) }
        }
    }

    fun showMenus(visible: Boolean) {
        mutableState.update { it.copy(menuVisible = visible) }
    }

    @Immutable
    data class State(
        val manga: Manga? = null,
        val source: Source? = null,
        val initError: Throwable? = null,
        val viewerChapters: ViewerChapters? = null,
        val bookmarked: Boolean = false,
        val isLoadingAdjacentChapter: Boolean = false,
        val currentPage: Int = -1,
        val menuVisible: Boolean = false,
        // desktop: counts the moves to another chapter the viewer has to jump for (see loadNextChapter)
        val chapterJumps: Int = 0,
    ) {
        val currentChapter: ReaderChapter?
            get() = viewerChapters?.currChapter

        val totalPages: Int
            get() = currentChapter?.pages?.size ?: -1
    }
}

// desktop: spec 2a §4 groups the progress writes every 500 ms (Mihon: 2 s)
private val PROGRESS_SAVE_DELAY = 500.milliseconds

// Mihon: the next chapter loads when fewer pages than this are left
private const val PRELOAD_RANGE = 5

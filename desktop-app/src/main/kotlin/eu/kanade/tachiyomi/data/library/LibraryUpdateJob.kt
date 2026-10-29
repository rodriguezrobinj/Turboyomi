package eu.kanade.tachiyomi.data.library

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.data.backup.BackupJob
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import logcat.LogPriority
import mihon.core.metro.AppCoroutineScope
import mihon.desktop.app.platform.AppMessages
import mihon.desktop.app.tray.TrayNotifier
import mihon.desktop.app.tray.errorsNotice
import mihon.desktop.app.tray.newChaptersNotice
import mihon.desktop.core.AppDirs
import mihon.desktop.core.jobs.PeriodicJob
import mihon.domain.chapter.interactor.FilterChaptersForDownload
import mihon.domain.source.interactor.UpdateMangaFromRemote
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.NoChaptersException
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_HAS_UNREAD
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_NON_COMPLETED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_NON_READ
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MANGA_OUTSIDE_RELEASE_PERIOD
import tachiyomi.domain.manga.interactor.FetchInterval
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.createDirectories
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Mihon's LibraryUpdateWorker and MetadataUpdateWorker without WorkManager (spec 3a §3 F): one update at a time in the
 * app scope, [progress] for the in-app bar and the tray tooltip, notifications through [TrayNotifier].
 */
@Inject
@SingleIn(AppScope::class)
class LibraryUpdateJob(
    private val context: Context,
    private val appDirs: AppDirs,
    private val sourceManager: SourceManager,
    private val libraryPreferences: LibraryPreferences,
    private val getLibraryManga: GetLibraryManga,
    private val getManga: GetManga,
    private val fetchInterval: FetchInterval,
    private val updateMangaFromRemote: UpdateMangaFromRemote,
    private val downloadManager: DownloadManager,
    private val filterChaptersForDownload: FilterChaptersForDownload,
    private val backupJob: BackupJob,
    @AppCoroutineScope private val scope: CoroutineScope,
) {
    /** [done] of [total] manga; [current] are the titles being fetched right now. */
    data class Progress(val done: Int, val total: Int, val current: List<String>)

    private val _progress = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = _progress.asStateFlow()

    @Volatile
    private var running: Job? = null

    /** Updates the chapters of [categoryId]'s manga, or of the whole library. False if an update is already running. */
    fun start(categoryId: Long? = null): Boolean = launch { updateChapters(categoryId) }

    /** Mihon's "Refresh library metadata". Shares the slot with [start]. */
    fun startMetadata(): Boolean = launch { updateMetadata() }

    /** Stops at the next manga; what was already saved stays. */
    fun cancel() {
        running?.cancel()
    }

    suspend fun awaitIdle() {
        running?.join()
    }

    /** The automatic update, for the scheduler: [LibraryPreferences.autoUpdateInterval] hours, 0 = off. */
    fun periodicJob(): PeriodicJob = PeriodicJob(
        name = "LibraryUpdate",
        interval = { libraryPreferences.autoUpdateInterval.get().takeIf { it > 0 }?.hours },
        lastRun = libraryPreferences.lastUpdatedTimestamp,
        // desktop: never during a restore, which is rewriting the library (spec 5a §3 E)
        run = {
            backupJob.awaitRestore()
            if (start()) awaitIdle()
        },
    )

    private fun launch(block: suspend () -> Unit): Boolean = synchronized(this) {
        if (running?.isActive == true) return false
        running = scope.launch(Dispatchers.IO) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Library update failed" }
                AppMessages.show(context.stringResource(MR.strings.internal_error))
            } finally {
                _progress.value = null
            }
        }
        true
    }

    private suspend fun updateChapters(categoryId: Long?) {
        libraryPreferences.lastUpdatedTimestamp.set(Clock.System.now().toEpochMilliseconds())
        val timeZone = TimeZone.currentSystemDefault()
        val fetchWindow = fetchInterval.getWindow(Clock.System.now().toLocalDateTime(timeZone).date, timeZone)
        val queue = queue(categoryId, fetchWindow.endInclusive)
        warnIfQueueTooBig(queue)

        val newUpdates = CopyOnWriteArrayList<Pair<Manga, List<Chapter>>>()
        val failed = CopyOnWriteArrayList<Pair<Manga, String?>>()
        val hasDownloads = AtomicBoolean(false)
        forEachWithProgress(queue) { manga ->
            // Don't continue to update if manga is not in library
            if (getManga.await(manga.id)?.favorite != true) return@forEachWithProgress
            try {
                val update = updateMangaFromRemote(
                    source = sourceManager.getOrStub(manga.source),
                    manga = manga,
                    fetchDetails = libraryPreferences.autoUpdateMetadata.get(),
                    fetchChapters = true,
                    fetchWindow = fetchWindow,
                ).getOrThrow()
                val newChapters = if (update.manga.favorite) update.newChapters else emptyList()
                if (newChapters.isNotEmpty()) {
                    // Sources update concurrently, and getAndSet is a separate read and write
                    synchronized(libraryPreferences.newUpdatesCount) {
                        libraryPreferences.newUpdatesCount.getAndSet { it + newChapters.size }
                    }
                    newUpdates += manga to newChapters.sortedByDescending { it.sourceOrder }
                    // desktop: Mihon's automatic download of new chapters (spec 4b §2)
                    val toDownload = filterChaptersForDownload.await(manga, newChapters)
                    if (toDownload.isNotEmpty()) {
                        downloadManager.downloadChapters(manga, toDownload, autoStart = false)
                        hasDownloads.set(true)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val message = when (e) {
                    is NoChaptersException -> context.stringResource(MR.strings.no_chapters_error)
                    // desktop: the same text the screens show, Cloudflare and the built-in browser included (spec 2b)
                    else -> with(context) { e.formattedMessage }
                }
                failed += manga to message
            }
        }

        if (hasDownloads.get()) downloadManager.startDownloads()
        if (newUpdates.isNotEmpty()) TrayNotifier.send(newChaptersNotice(context, newUpdates))
        if (failed.isNotEmpty()) {
            val file = writeErrorFile(failed)
            TrayNotifier.send(errorsNotice(context, failed.size))
            AppMessages.show(
                context.pluralStringResource(MR.plurals.notification_update_error, failed.size, failed.size),
                context.stringResource(MR.strings.desktop_open_log),
            ) { runCatching { Desktop.getDesktop().open(file) } }
        }
    }

    private suspend fun updateMetadata() {
        val queue = getLibraryManga.await()
        warnIfQueueTooBig(queue)
        forEachWithProgress(queue) { manga ->
            val source = sourceManager.get(manga.source) ?: return@forEachWithProgress
            // Errors are ignored and logged inside, like Mihon's MetadataUpdateWorker
            updateMangaFromRemote(source = source, manga = manga, fetchDetails = true)
        }
    }

    /** Mihon's addMangaToQueue: the requested category or the included and excluded ones, then the restrictions. */
    private suspend fun queue(categoryId: Long?, fetchWindowUpperBound: Instant): List<LibraryManga> {
        val libraryManga = getLibraryManga.await()
        val listToUpdate = if (categoryId != null) {
            libraryManga.filter { categoryId in it.categories }
        } else {
            val included = libraryPreferences.updateCategories.get().map { it.toLong() }
            val excluded = libraryPreferences.updateCategoriesExclude.get().map { it.toLong() }
            libraryManga.filter {
                (included.isEmpty() || it.categories.intersect(included).isNotEmpty()) &&
                    it.categories.intersect(excluded).isEmpty()
            }
        }
        val restrictions = libraryPreferences.autoUpdateMangaRestrictions.get()
        val skipped = mutableListOf<Pair<String, String>>()
        val queue = listToUpdate
            .filter {
                val reason = when {
                    it.manga.updateStrategy == UpdateStrategy.ONLY_FETCH_ONCE && it.totalChapters > 0L ->
                        "not always update"
                    MANGA_NON_COMPLETED in restrictions && it.manga.status.toInt() == SManga.COMPLETED -> "completed"
                    MANGA_HAS_UNREAD in restrictions && it.unreadCount != 0L -> "not caught up"
                    MANGA_NON_READ in restrictions && it.totalChapters > 0L && !it.hasStarted -> "not started"
                    MANGA_OUTSIDE_RELEASE_PERIOD in restrictions &&
                        it.manga.nextUpdate.let { next -> next != null && next > fetchWindowUpperBound } ->
                        "not in release period"
                    else -> null
                }
                if (reason != null) skipped += reason to it.manga.title
                reason == null
            }
            .sortedBy { it.manga.title }
        if (skipped.isNotEmpty()) {
            logcat { skipped.groupBy({ it.first }, { it.second }).entries.joinToString { "${it.key}: ${it.value}" } }
        }
        return queue
    }

    /** Up to 5 sources at a time, one manga after another inside each, like Mihon. */
    private suspend fun forEachWithProgress(queue: List<LibraryManga>, block: suspend (Manga) -> Unit) {
        _progress.value = Progress(done = 0, total = queue.size, current = emptyList())
        val semaphore = Semaphore(5)
        coroutineScope {
            queue.groupBy { it.manga.source }.values
                .sortedByDescending { it.size }
                .map { inSource ->
                    async {
                        semaphore.withPermit {
                            inSource.forEach { item ->
                                ensureActive()
                                val title = item.manga.title
                                _progress.update { it?.copy(current = it.current + title) }
                                block(item.manga)
                                ensureActive()
                                _progress.update { it?.copy(done = it.done + 1, current = it.current - title) }
                            }
                        }
                    }
                }
                .awaitAll()
        }
    }

    // desktop: an in-app message with a link instead of Mihon's warning notification
    private suspend fun warnIfQueueTooBig(queue: List<LibraryManga>) {
        val maxFromSource = queue.groupBy { it.manga.source }
            .filter { (sourceId, _) -> sourceManager.get(sourceId) !is UnmeteredSource }
            .maxOfOrNull { it.value.size } ?: 0
        if (maxFromSource <= MANGA_PER_SOURCE_QUEUE_WARNING_THRESHOLD) return
        AppMessages.show(
            context.stringResource(MR.strings.notification_size_warning),
            context.stringResource(MR.strings.desktop_learn_more),
        ) { runCatching { Desktop.getDesktop().browse(URI(HELP_WARNING_URL)) } }
    }

    /** Mihon's error file format, in the logs folder (spec 3a §3 G). */
    private suspend fun writeErrorFile(errors: List<Pair<Manga, String?>>): File {
        val file = appDirs.logs.createDirectories().resolve("update_errors.txt").toFile()
        file.bufferedWriter().use { out ->
            out.write(context.stringResource(MR.strings.library_errors_help, ERROR_LOG_HELP_URL) + "\n\n")
            // Error file format:
            // ! Error
            //   # Source
            //     - Manga
            errors.groupBy({ it.second }, { it.first }).forEach { (error, mangas) ->
                out.write("\n! ${error}\n")
                mangas.groupBy { it.source }.forEach { (srcId, inSource) ->
                    out.write("  # ${sourceManager.getOrStub(srcId)}\n")
                    inSource.forEach { out.write("    - ${it.title}\n") }
                }
            }
        }
        return file
    }

    private companion object {
        const val MANGA_PER_SOURCE_QUEUE_WARNING_THRESHOLD = 60
        const val ERROR_LOG_HELP_URL = "https://mihon.app/docs/guides/troubleshooting/"
        const val HELP_WARNING_URL =
            "https://mihon.app/docs/faq/library#why-am-i-warned-about-large-bulk-updates-and-downloads"
    }
}

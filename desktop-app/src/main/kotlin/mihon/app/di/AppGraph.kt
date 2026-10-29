package mihon.app.di

import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.domain.track.interactor.TrackChapter
import eu.kanade.domain.track.service.DelayedTrackingJob
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.track.store.DelayedTrackingStore
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.data.backup.BackupJob
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.CrashLogUtil
import mihon.data.cache.ChapterCache
import mihon.desktop.app.track.PendingLogins
import mihon.desktop.app.track.TrackLoginHandler
import mihon.desktop.app.tray.TrayPreferences
import mihon.desktop.core.DesktopGraph
import mihon.domain.extension.interactor.GetExtensionStoreCountAsFlow
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.ResetCategoryFlags
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.interactor.ResetViewerFlags
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.domain.storage.service.StoragePreferences
import tachiyomi.domain.track.interactor.UpsertTrack

/**
 * Desktop counterpart of Mihon's AppGraph: the accessors ported code reaches through `context.appGraph`.
 * The graph itself is DesktopAppGraph (tests: TestAppGraph).
 */
interface AppGraph : DesktopGraph, ViewModelGraph {
    val uiPreferences: UiPreferences
    val basePreferences: BasePreferences
    val coverCache: CoverCache
    val chapterCache: ChapterCache
    val mihonExtensionManager: ExtensionManager
    val sourcePreferences: SourcePreferences
    val readerPreferences: ReaderPreferences
    val getExtensionStoreCountAsFlow: GetExtensionStoreCountAsFlow
    val libraryPreferences: LibraryPreferences
    val libraryUpdateJob: LibraryUpdateJob
    val getCategories: GetCategories
    val resetCategoryFlags: ResetCategoryFlags
    val trayPreferences: TrayPreferences
    val storagePreferences: StoragePreferences
    val storageManager: StorageManager
    val downloadManager: DownloadManager
    val downloadPreferences: DownloadPreferences
    val downloadCache: DownloadCache
    val crashLogUtil: CrashLogUtil
    val resetViewerFlags: ResetViewerFlags
    val backupPreferences: BackupPreferences
    val getFavorites: GetFavorites
    val backupJob: BackupJob

    // desktop: the app's own updates (spec 6b §2)
    val appUpdater: mihon.desktop.app.update.AppUpdater
    val backupNotifier: BackupNotifier
    val trackPreferences: TrackPreferences
    val trackerManager: TrackerManager
    val addTracks: AddTracks
    val upsertTrack: UpsertTrack
    val trackChapter: TrackChapter
    val delayedTrackingStore: DelayedTrackingStore
    val delayedTrackingJob: DelayedTrackingJob
    val trackLoginHandler: TrackLoginHandler
    val pendingLogins: PendingLogins
}

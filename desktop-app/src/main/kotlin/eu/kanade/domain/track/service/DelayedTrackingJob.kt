package eu.kanade.domain.track.service

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.track.interactor.TrackChapter
import eu.kanade.domain.track.store.DelayedTrackingStore
import logcat.LogPriority
import mihon.desktop.core.jobs.PeriodicJob
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.track.interactor.GetTracks
import kotlin.time.Duration.Companion.minutes

/**
 * Desktop counterpart of Mihon's DelayedTrackingUpdateWorker (spec 5b-1 §3 C): while tracking updates are queued, it
 * sends them every few minutes, whenever the app is open or in the tray.
 */
@Inject
@SingleIn(AppScope::class)
class DelayedTrackingJob(
    private val context: Context,
    private val getTracks: GetTracks,
    private val trackChapter: TrackChapter,
    private val delayedTrackingStore: DelayedTrackingStore,
    preferenceStore: PreferenceStore,
) {
    private val lastRun = preferenceStore.getLong(Preference.appStateKey("desktop_last_delayed_tracking"), 0L)

    fun periodicJob(): PeriodicJob = PeriodicJob(
        name = "DelayedTracking",
        interval = { RETRY.takeIf { delayedTrackingStore.getItems().isNotEmpty() } },
        lastRun = lastRun,
        run = ::flush,
    )

    /** Mihon's doWork: each queued item goes up again; TrackChapter takes it off the queue once it lands. */
    suspend fun flush() {
        delayedTrackingStore.getItems()
            .mapNotNull {
                val track = getTracks.awaitOne(it.trackId)
                if (track == null) delayedTrackingStore.remove(it.trackId)
                track?.copy(lastChapterRead = it.lastChapterRead.toDouble())
            }
            .forEach { track ->
                logcat(LogPriority.DEBUG) { "Updating delayed track item: ${track.mangaId}, ${track.lastChapterRead}" }
                trackChapter.await(context, track.mangaId, track.lastChapterRead, setupJobOnFailure = false)
            }
    }

    private companion object {
        val RETRY = 5.minutes
    }
}

package eu.kanade.tachiyomi.data.download

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mihon.core.metro.AppCoroutineScope
import kotlin.time.Duration.Companion.milliseconds

/**
 * Desktop counterpart of Mihon's DownloadWorker (spec 4b §2): runs the downloader on the app scope, which outlives
 * the window while the app is in the tray, and says whether it's running.
 */
@Inject
@SingleIn(AppScope::class)
class DownloadJob(
    private val downloader: Downloader,
    @AppCoroutineScope private val scope: CoroutineScope,
) {
    private val running = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = running.asStateFlow()

    private var watcher: Job? = null

    /**
     * Starts the downloader; it does nothing if it's already running or has nothing to do. It always asks the
     * downloader rather than trusting a flag of its own: a start right after a run ends would otherwise find that run
     * still being watched and be dropped.
     */
    fun start() {
        scope.launch { downloader.start() }
        watch()
    }

    // ponytail: polls Downloader.isRunning (Mihon's, not a flow) while the app is open; a flow from it if it ever has one
    @Synchronized
    private fun watch() {
        if (watcher != null) return
        watcher = scope.launch {
            while (true) {
                running.value = downloader.isRunning
                delay(POLL)
            }
        }
    }

    private companion object {
        val POLL = 250.milliseconds
    }
}

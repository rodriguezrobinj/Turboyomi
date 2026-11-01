package mihon.desktop.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import kotlinx.coroutines.flow.map
import mihon.app.di.AppGraph
import java.awt.Taskbar
import java.awt.Window

/** The taskbar button's bar (spec 12 §6): [percent] done, yellow when [paused]. */
data class TaskbarBar(val percent: Int, val paused: Boolean)

/**
 * The library update first, else the download queue since it was last empty: [peak] is the most chapters it held,
 * [queued] how many are left, [chapterPercent] the one downloading now. Null when neither runs.
 */
fun taskbarBar(
    library: LibraryUpdateJob.Progress?,
    queued: Int,
    peak: Int,
    chapterPercent: Int,
    downloading: Boolean,
): TaskbarBar? = when {
    library != null && library.total > 0 -> TaskbarBar(library.done * 100 / library.total, paused = false)
    queued > 0 -> {
        val total = maxOf(peak, queued)
        TaskbarBar((((total - queued) * 100 + chapterPercent) / total).coerceAtMost(100), paused = !downloading)
    }
    else -> null
}

/** Shows the library update and the downloads on [window]'s taskbar button, where Windows supports it. */
@Composable
fun TaskbarProgress(graph: AppGraph, window: Window) {
    val taskbar = remember {
        Taskbar.isTaskbarSupported() &&
            Taskbar.getTaskbar().isSupported(Taskbar.Feature.PROGRESS_STATE_WINDOW) &&
            Taskbar.getTaskbar().isSupported(Taskbar.Feature.PROGRESS_VALUE_WINDOW)
    }
    if (!taskbar) return
    val library by graph.libraryUpdateJob.progress.collectAsState()
    val queue by graph.downloadManager.queueState.collectAsState()
    val downloading by graph.downloadManager.isDownloaderRunning.collectAsState()
    // Not state: it only follows the queue
    val peak = remember { intArrayOf(0) }
    peak[0] = if (queue.isEmpty()) 0 else maxOf(peak[0], queue.size)
    // The chapter whose pages moved last: the one downloading
    val chapterPercent by remember { graph.downloadManager.progressFlow().map { it.progress } }.collectAsState(0)
    val bar = taskbarBar(library, queue.size, peak[0], chapterPercent, downloading)
    LaunchedEffect(bar) {
        val taskbar = Taskbar.getTaskbar()
        runCatching {
            if (bar == null) {
                taskbar.setWindowProgressState(window, Taskbar.State.OFF)
            } else {
                taskbar.setWindowProgressState(window, if (bar.paused) Taskbar.State.PAUSED else Taskbar.State.NORMAL)
                taskbar.setWindowProgressValue(window, bar.percent)
            }
        }
    }
}

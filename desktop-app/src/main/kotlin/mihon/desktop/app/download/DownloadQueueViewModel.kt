package mihon.desktop.app.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The download queue (spec 4b §3 B): Mihon's DownloadQueueViewModel without its RecyclerView adapter. */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class DownloadQueueViewModel(private val downloadManager: DownloadManager) : ViewModel() {

    /** The queue grouped by source, in queue order. */
    val state: StateFlow<List<Pair<HttpSource, List<Download>>>> = downloadManager.queueState
        .map { queue -> queue.groupBy { it.source }.toList() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val isDownloaderRunning: StateFlow<Boolean> = downloadManager.isDownloaderRunning

    fun startDownloads() = downloadManager.startDownloads()

    fun pauseDownloads() {
        viewModelScope.launch { downloadManager.pauseDownloads() }
    }

    fun clearQueue() {
        viewModelScope.launch { downloadManager.clearQueue() }
    }

    fun cancel(downloads: List<Download>) {
        viewModelScope.launch { downloadManager.cancelQueuedDownloads(downloads) }
    }

    /** A drag ended: [download] goes to [index] of the whole queue. */
    fun move(download: Download, index: Int) {
        val queue = downloadManager.queueState.value.toMutableList()
        queue.remove(download)
        queue.add(index.coerceIn(0, queue.size), download)
        reorder(queue)
    }

    fun moveToTop(download: Download, series: Boolean) = moveTo(download, series, top = true)

    fun moveToBottom(download: Download, series: Boolean) = moveTo(download, series, top = false)

    /** Mihon's sort: each source keeps its place, and its chapters are sorted by [selector]. */
    fun <R : Comparable<R>> sort(selector: (Download) -> R, descending: Boolean) {
        reorder(
            state.value.flatMap { (_, downloads) ->
                if (descending) downloads.sortedByDescending(selector) else downloads.sortedBy(selector)
            },
        )
    }

    private fun moveTo(download: Download, series: Boolean, top: Boolean) {
        val queue = downloadManager.queueState.value
        val (moved, others) = queue.partition {
            if (series) it.manga.id == download.manga.id else it == download
        }
        reorder(if (top) moved + others else others + moved)
    }

    private fun reorder(downloads: List<Download>) {
        viewModelScope.launch { downloadManager.reorderQueue(downloads) }
    }
}

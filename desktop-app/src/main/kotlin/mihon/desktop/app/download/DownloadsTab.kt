package mihon.desktop.app.download

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.tab.TabOptions
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.data.download.model.Download
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.DragHandle
import mihon.icons.materialsymbols.rounded.MoreVert
import mihon.icons.materialsymbols.roundedfilled.Pause
import mihon.icons.materialsymbols.roundedfilled.PlayArrow
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.util.keyboardScrollable
import mihon.icons.materialsymbols.rounded.Download as DownloadIcon

/** Rail › Downloads (spec 4b §3 B): the queue, grouped by source, with drag to reorder. */
data object DownloadsTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(
            index = 2u,
            title = stringResource(MR.strings.pref_category_downloads),
            icon = rememberVectorPainter(MaterialSymbols.Rounded.DownloadIcon),
        )

    @Composable
    override fun Content() {
        val viewModel = metroViewModel<DownloadQueueViewModel>()
        val groups by viewModel.state.collectAsStateWithLifecycle()
        val running by viewModel.isDownloaderRunning.collectAsStateWithLifecycle()

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.label_download_queue),
                    scrollBehavior = scrollBehavior,
                    actions = {
                        if (groups.isNotEmpty()) {
                            AppBarActions(
                                listOf(
                                    AppBar.OverflowAction(stringResource(MR.strings.action_order_by_upload_date)) {
                                        viewModel.sort(
                                            { it.chapter.dateUpload?.toEpochMilliseconds() ?: 0L },
                                            descending = false,
                                        )
                                    },
                                    AppBar.OverflowAction(stringResource(MR.strings.action_order_by_chapter_number)) {
                                        viewModel.sort({ it.chapter.chapterNumber }, descending = false)
                                    },
                                    AppBar.OverflowAction(stringResource(MR.strings.action_cancel_all)) {
                                        viewModel.clearQueue()
                                    },
                                ),
                            )
                        }
                    },
                )
            },
            floatingActionButton = {
                if (groups.isNotEmpty()) {
                    val label = stringResource(if (running) MR.strings.action_pause else MR.strings.action_resume)
                    ExtendedFloatingActionButton(
                        text = { Text(label) },
                        icon = {
                            Icon(
                                if (running) {
                                    MaterialSymbols.RoundedFilled.Pause
                                } else {
                                    MaterialSymbols.RoundedFilled.PlayArrow
                                },
                                contentDescription = label,
                            )
                        },
                        onClick = { if (running) viewModel.pauseDownloads() else viewModel.startDownloads() },
                    )
                }
            },
        ) { contentPadding ->
            if (groups.isEmpty()) {
                EmptyScreen(MR.strings.information_no_downloads, modifier = Modifier.padding(contentPadding))
                return@Scaffold
            }
            val queue = groups.flatMap { it.second }
            val listState = rememberLazyListState()
            val reorderState = rememberReorderableLazyListState(listState) { from, to ->
                val moved = queue.firstOrNull { it.chapter.id == from.key } ?: return@rememberReorderableLazyListState
                val target = queue.indexOfFirst { it.chapter.id == to.key }
                if (target >= 0) viewModel.move(moved, target)
            }
            LazyColumn(
                modifier = Modifier.keyboardScrollable(listState),
                state = listState,
                contentPadding = contentPadding,
            ) {
                groups.forEach { (source, downloads) ->
                    item(key = "source-${source.id}") {
                        Text(
                            text = "${source.name} (${downloads.size})",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(MaterialTheme.padding.medium),
                        )
                    }
                    items(downloads, key = { it.chapter.id }) { download ->
                        ReorderableItem(reorderState, key = download.chapter.id) {
                            DownloadRow(
                                download = download,
                                dragHandle = {
                                    Icon(
                                        MaterialSymbols.Rounded.DragHandle,
                                        contentDescription = null,
                                        modifier = Modifier.draggableHandle(),
                                    )
                                },
                                viewModel = viewModel,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(download: Download, dragHandle: @Composable () -> Unit, viewModel: DownloadQueueViewModel) {
    val progress by download.progressFlow.collectAsStateWithLifecycle(0)
    val status by download.statusFlow.collectAsStateWithLifecycle()
    val pages by download.pagesFlow.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.padding.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dragHandle()
        Column(Modifier.weight(1f).padding(horizontal = MaterialTheme.padding.small)) {
            Text(download.manga.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                download.chapter.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (status == Download.State.ERROR) {
                Text(stringResource(MR.strings.download_notifier_title_error), color = MaterialTheme.colorScheme.error)
            } else {
                LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                pages?.let {
                    Text("${download.downloadedImages}/${it.size}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        IconButton(onClick = { menu = true }) {
            Icon(
                MaterialSymbols.Rounded.MoreVert,
                contentDescription = stringResource(MR.strings.action_menu_overflow_description),
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            listOf(
                MR.strings.action_move_to_top to { viewModel.moveToTop(download, series = false) },
                MR.strings.action_move_to_top_all_for_series to { viewModel.moveToTop(download, series = true) },
                MR.strings.action_move_to_bottom to { viewModel.moveToBottom(download, series = false) },
                MR.strings.action_move_to_bottom_all_for_series to { viewModel.moveToBottom(download, series = true) },
                MR.strings.action_cancel to { viewModel.cancel(listOf(download)) },
                MR.strings.cancel_all_for_series to {
                    val series = viewModel.state.value.flatMap { it.second }.filter { it.manga.id == download.manga.id }
                    viewModel.cancel(series)
                },
            ).forEach { (title, action) ->
                DropdownMenuItem(
                    text = { Text(stringResource(title)) },
                    onClick = {
                        menu = false
                        action()
                    },
                )
            }
        }
    }
}

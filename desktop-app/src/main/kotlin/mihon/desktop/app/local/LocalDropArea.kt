package mihon.desktop.app.local

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.app.di.AppGraph
import mihon.desktop.app.platform.AppMessages
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.source.local.LocalSource
import java.net.URI
import java.nio.file.Path

/** Folders and chapter archives dropped anywhere on the window go to the local source (spec 12 §5). */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LocalDropArea(graph: AppGraph, navigator: Navigator, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val target = remember(navigator) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val files = (event.dragData() as? DragData.FilesList)?.readFiles() ?: return false
                scope.launch { import(graph, navigator, files.map { Path.of(URI(it)) }) }
                return true
            }
        }
    }
    Box(
        Modifier.fillMaxSize().dragAndDropTarget(
            shouldStartDragAndDrop = { it.dragData() is DragData.FilesList },
            target = target,
        ),
    ) {
        content()
    }
}

private suspend fun import(graph: AppGraph, navigator: Navigator, dropped: List<Path>) {
    val context = graph.context
    AppMessages.show(context.stringResource(MR.strings.desktop_local_drop_copying))
    val result = withContext(Dispatchers.IO) {
        runCatching {
            val local = graph.storageManager.getLocalSourceDirectory()?.filePath
                ?: error(context.stringResource(MR.strings.no_location_set))
            LocalDrop.import(dropped, Path.of(local))
        }
    }
    result.onFailure {
        LocalDrop.logcat(LogPriority.ERROR, it) { "Copying dropped files to the local source failed" }
        AppMessages.show(context.stringResource(MR.strings.desktop_local_drop_failed, it.message ?: it.toString()))
    }
    val (mangas, unsupported) = result.getOrNull() ?: return
    if (unsupported.isNotEmpty()) {
        AppMessages.show(
            context.stringResource(MR.strings.desktop_local_drop_unsupported, unsupported.joinToString()),
        )
    }
    if (mangas.isNotEmpty()) {
        AppMessages.show(
            context.stringResource(MR.strings.desktop_local_drop_added, mangas.joinToString()),
            context.stringResource(MR.strings.local_source),
        ) {
            // One manga: the local source searching for it, as its listing shows only what matches
            navigator.push(BrowseSourceScreen(LocalSource.ID, mangas.singleOrNull()))
        }
    }
}

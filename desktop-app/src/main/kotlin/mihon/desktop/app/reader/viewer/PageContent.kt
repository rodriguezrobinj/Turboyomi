package mihon.desktop.app.reader.viewer

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import mihon.desktop.app.reader.ImageScale
import mihon.desktop.app.reader.image.DecodedPage
import mihon.desktop.app.reader.image.DecodedPageCache
import mihon.desktop.app.reader.image.PageDecoder
import mihon.desktop.app.reader.image.PageKey
import net.engawapg.lib.zoomable.MouseWheelZoom
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * One page: loads it, decodes it once it's downloaded and draws it. Failures stay on this page with a retry
 * (spec 2a §4, "Errores del lector"). With [zoom], double click and Ctrl+wheel zoom and dragging pans; the zoom
 * starts over whenever the page becomes the current one again.
 */
@Composable
fun PageContent(
    page: ReaderPage,
    isCurrent: Boolean,
    scale: ImageScale,
    cache: DecodedPageCache,
    zoom: Boolean,
    onTap: (x: Float, width: Float) -> Unit,
    onRetry: (ReaderPage) -> Unit,
    onSize: (IntSize) -> Unit,
    modifier: Modifier = Modifier,
    onSave: (ReaderPage) -> Unit = {},
    onCopy: (ReaderPage) -> Unit = {},
) {
    val saveLabel = stringResource(MR.strings.action_save)
    val copyLabel = stringResource(MR.strings.action_copy_to_clipboard)
    val status by page.statusFlow.collectAsStateWithLifecycle()
    LaunchedEffect(page) { page.chapter.pageLoader?.loadPage(page) }

    val key = PageKey(page.chapter.chapter.id, page.index)
    val decoded by produceState<Result<DecodedPage>?>(cache.get(key)?.let(Result.Companion::success), status) {
        if (value?.isSuccess == true || status != Page.State.Ready) return@produceState
        val stream = page.stream ?: return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching { PageDecoder.decode(stream().use { it.readBytes() }) }
        }.onSuccess { cache.put(key, it) }
    }

    // The page's width, for the click zones of zoomable's onTap, which only gives the offset
    var width by remember { mutableIntStateOf(1) }
    val tapModifier = Modifier.pointerInput(page) {
        detectTapGestures { offset -> onTap(offset.x, size.width.toFloat()) }
    }

    val result = decoded
    // Also for a page that comes decoded from the cache, so a new viewer learns its size
    LaunchedEffect(result) { result?.getOrNull()?.let { onSize(IntSize(it.width, it.height)) } }
    Box(
        modifier
            .onSizeChanged { width = it.width }
            .testTag(if (result?.isSuccess == true) "page ${page.number} ready" else "page ${page.number}"),
    ) {
        when {
            status is Page.State.Error || result?.isFailure == true -> PageError(page, onRetry, tapModifier)
            result == null -> PageLoading(page, tapModifier)
            else -> {
                // A new zoom state each time the page becomes current: the zoom starts over (spec 2a §4)
                val zoomState = key(page, isCurrent) { rememberZoomState(maxScale = 5f) }
                val pageModifier = if (zoom) {
                    Modifier.fillMaxSize().zoomable(
                        zoomState = zoomState,
                        onTap = { offset: Offset -> onTap(offset.x, width.toFloat()) },
                        mouseWheelZoom = MouseWheelZoom.EnabledWithCtrlKey,
                    )
                } else {
                    tapModifier.fillMaxWidth()
                }
                // Right click (spec 2a §4): save the page as it came, or copy it as an image
                ContextMenuArea(
                    items = {
                        listOf(
                            ContextMenuItem(saveLabel) { onSave(page) },
                            ContextMenuItem(copyLabel) { onCopy(page) },
                        )
                    },
                ) {
                    DecodedPageImage(
                        result.getOrThrow(),
                        if (zoom) scale.contentScale else ContentScale.FillWidth,
                        pageModifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun PageLoading(page: ReaderPage, modifier: Modifier) {
    val progress by page.progressFlow.collectAsStateWithLifecycle()
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (progress in
            1..99
        ) {
            CircularProgressIndicator(progress = { progress / 100f })
        } else {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun PageError(page: ReaderPage, onRetry: (ReaderPage) -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(MR.strings.decode_image_error), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = { onRetry(page) }) { Text(stringResource(MR.strings.action_retry)) }
    }
}

@Composable
fun DecodedPageImage(page: DecodedPage, contentScale: ContentScale, modifier: Modifier) {
    when (page) {
        is DecodedPage.Still -> if (page.tiles.size == 1) {
            Image(page.tiles.single(), contentDescription = null, contentScale = contentScale, modifier = modifier)
        } else {
            // Very tall page: its strips stacked, each as wide as the page
            Column(modifier) {
                page.tiles.forEach {
                    Image(
                        it,
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        is DecodedPage.Animated -> {
            var frame by remember(page) { mutableIntStateOf(0) }
            LaunchedEffect(page) {
                while (true) {
                    delay(page.durationsMs[frame].toLong())
                    frame = (frame + 1) % page.frames.size
                }
            }
            Image(page.frames[frame], contentDescription = null, contentScale = contentScale, modifier = modifier)
        }
    }
}

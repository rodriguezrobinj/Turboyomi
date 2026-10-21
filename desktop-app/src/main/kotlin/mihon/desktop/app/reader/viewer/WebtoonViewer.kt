package mihon.desktop.app.reader.viewer

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.reader.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import kotlinx.coroutines.flow.distinctUntilChanged
import mihon.desktop.app.reader.ImageScale
import mihon.desktop.app.reader.ReaderAction
import mihon.desktop.app.reader.Spread
import mihon.desktop.app.reader.image.DecodedPageCache
import mihon.desktop.app.reader.tapActionFor

const val WEBTOON_TAG = "webtoon"

/**
 * Webtoon (spec 2a §4): pages one under another with no space between them ([gap]: Mihon's continuous vertical, 16
 * dp), [sidePaddingPercent] of the width on each side. Going on past the end (wheel, keys) loads the next chapter:
 * only reaching it would leave the chapter as soon as the way on shows under its last page.
 */
@Composable
fun WebtoonViewer(
    chapters: ViewerChapters,
    gap: Boolean,
    sidePaddingPercent: Int,
    cache: DecodedPageCache,
    onPageSelected: (ReaderPage) -> Unit,
    onReachedEnd: () -> Unit,
    onTap: (ReaderAction) -> Unit,
    onRetry: (ReaderPage) -> Unit,
    onController: (ViewerController) -> Unit,
) {
    val current = chapters.currChapter
    val pages = current.pages.orEmpty()
    val items = remember(chapters) { viewerItems(chapters, pages.indices.map { Spread.Single(it) }) }
    // Decoded sizes: until a page has one it's as tall as the window, so a jump (End, resuming) lands on it
    // instead of on a page above that grows once it loads
    val sizes = remember(current) { mutableStateMapOf<Int, IntSize>() }
    val latestItems by rememberUpdatedState(items)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = items.itemOfPage(current.requestedPage))

    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex }.distinctUntilChanged().collect { index ->
            val item = latestItems.getOrNull(index) as? ViewerItem.Pages ?: return@collect
            onPageSelected(item.pages.single())
            cache.retainAround(current.chapter.id, item.pages.single().index)
        }
    }
    val latestOnReachedEnd by rememberUpdatedState(onReachedEnd)
    val controller = remember(state) { WebtoonController(state, { latestItems }) { latestOnReachedEnd() } }
    LaunchedEffect(controller) { onController(controller) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val side = maxWidth * sidePaddingPercent / 100
        LazyColumn(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .testTag(WEBTOON_TAG)
                .onPointerEvent(PointerEventType.Scroll) { event ->
                    if (event.changes.first().scrollDelta.y > 0 && !state.canScrollForward) latestOnReachedEnd()
                },
            contentPadding = PaddingValues(horizontal = side),
            verticalArrangement = Arrangement.spacedBy(if (gap) 16.dp else 0.dp),
        ) {
            itemsIndexed(items) { _, item ->
                when (item) {
                    is ViewerItem.Transition -> Box(
                        Modifier.fillMaxWidth().heightIn(min = 240.dp).pointerInput(item) {
                            detectTapGestures { onTap(tapActionFor(it.x, size.width.toFloat(), ReadingMode.WEBTOON)) }
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        ChapterTransition(
                            item.transition,
                            currChapterDownloaded = false,
                            goingToChapterDownloaded = false,
                        )
                    }
                    is ViewerItem.Pages -> PageContent(
                        page = item.pages.single(),
                        isCurrent = false,
                        scale = ImageScale.FIT_WIDTH,
                        cache = cache,
                        // spec deviation 5: no zoom on a scrolling list
                        zoom = false,
                        onTap = { x, width -> onTap(tapActionFor(x, width, ReadingMode.WEBTOON)) },
                        onRetry = onRetry,
                        onSize = { sizes[item.pages.single().index] = it },
                        modifier = if (item.pages.single().index in sizes) {
                            Modifier.fillMaxWidth()
                        } else {
                            Modifier.fillMaxWidth().fillParentMaxHeight()
                        },
                    )
                }
            }
        }
    }
}

private class WebtoonController(
    private val state: LazyListState,
    private val items: () -> List<ViewerItem>,
    private val onPastEnd: () -> Unit,
) : ViewerController {
    override suspend fun perform(action: ReaderAction) {
        val viewport = state.layoutInfo.viewportSize.height.toFloat()
        val forward = action == ReaderAction.NextPage || action == ReaderAction.ScrollDown
        if (forward && !state.canScrollForward) return onPastEnd()
        when (action) {
            ReaderAction.NextPage -> state.animateScrollBy(viewport * 0.9f)
            ReaderAction.PreviousPage -> state.animateScrollBy(-viewport * 0.9f)
            ReaderAction.ScrollDown -> state.animateScrollBy(viewport * 0.15f)
            ReaderAction.ScrollUp -> state.animateScrollBy(-viewport * 0.15f)
            ReaderAction.FirstPage -> state.scrollToItem(1)
            ReaderAction.LastPage -> state.scrollToItem((items().lastIndex - 1).coerceAtLeast(0))
            else -> Unit
        }
    }

    override suspend fun goToPage(index: Int) {
        state.scrollToItem(items().itemOfPage(index))
    }
}

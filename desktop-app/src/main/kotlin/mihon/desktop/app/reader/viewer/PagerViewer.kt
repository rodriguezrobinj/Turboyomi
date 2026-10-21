package mihon.desktop.app.reader.viewer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import eu.kanade.presentation.reader.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import kotlinx.coroutines.launch
import mihon.desktop.app.reader.ImageScale
import mihon.desktop.app.reader.ReaderAction
import mihon.desktop.app.reader.Spread
import mihon.desktop.app.reader.image.DecodedPageCache
import mihon.desktop.app.reader.inDisplayOrder
import mihon.desktop.app.reader.pairPages
import mihon.desktop.app.reader.tapActionFor

/**
 * The paged modes (spec 2a §4): left to right and right to left with a HorizontalPager (right to left reversed),
 * vertical with a VerticalPager. With [dual], pages pair two by two once their sizes are known.
 */
@Composable
fun PagerViewer(
    chapters: ViewerChapters,
    mode: ReadingMode,
    dual: Boolean,
    shifted: Boolean,
    scale: ImageScale,
    cache: DecodedPageCache,
    onPageSelected: (ReaderPage) -> Unit,
    onPastEnd: (forward: Boolean) -> Unit,
    onTap: (ReaderAction) -> Unit,
    onRetry: (ReaderPage) -> Unit,
    onController: (ViewerController) -> Unit,
    onSave: (ReaderPage) -> Unit,
    onCopy: (ReaderPage) -> Unit,
) {
    val current = chapters.currChapter
    val pages = current.pages.orEmpty()
    val sizes = remember(current) { mutableStateMapOf<Int, IntSize>() }
    val spreads = if (dual && mode.direction == ReadingMode.Direction.Horizontal) {
        pairPages(pages.size, { sizes[it]?.let { s -> s.width > s.height } ?: false }, shifted)
    } else {
        pages.indices.map { Spread.Single(it) }
    }
    val items = remember(chapters, spreads) { viewerItems(chapters, spreads) }
    val latestItems by rememberUpdatedState(items)
    val state = rememberPagerState(initialPage = items.itemOfPage(current.requestedPage)) { latestItems.size }
    val rightToLeft = mode == ReadingMode.RIGHT_TO_LEFT

    // Pairs change as page sizes become known: stay on the page being read
    LaunchedEffect(items) {
        val target = items.itemOfPage(current.requestedPage)
        if (state.currentPage != target) state.scrollToPage(target)
    }
    LaunchedEffect(state) {
        snapshotFlow { state.settledPage }.collect { index ->
            val item = latestItems.getOrNull(index) as? ViewerItem.Pages ?: return@collect
            // The last page of a pair, so reaching the end of the chapter marks it read
            onPageSelected(item.pages.last())
            cache.retainAround(current.chapter.id, item.pages.last().index)
        }
    }
    val controller = remember(state) { PagerController(state, { latestItems }, onPastEnd) }
    LaunchedEffect(controller) { onController(controller) }

    val scope = rememberCoroutineScope()
    val pagerModifier = Modifier.fillMaxSize().onPointerEvent(PointerEventType.Scroll) { event ->
        // The wheel turns pages; Ctrl+wheel is the page's zoom
        if (event.keyboardModifiers.isCtrlPressed) return@onPointerEvent
        val delta = event.changes.first().scrollDelta.y
        scope.launch { controller.perform(if (delta > 0) ReaderAction.NextPage else ReaderAction.PreviousPage) }
    }
    val pageOf: @Composable (Int) -> Unit = { index ->
        when (val item = items[index]) {
            is ViewerItem.Transition -> Box(
                Modifier.fillMaxSize().pointerInput(item) {
                    detectTapGestures { onTap(tapActionFor(it.x, size.width.toFloat(), mode)) }
                },
                contentAlignment = Alignment.Center,
            ) {
                ChapterTransition(item.transition, currChapterDownloaded = false, goingToChapterDownloaded = false)
            }
            is ViewerItem.Pages -> Row(Modifier.fillMaxSize()) {
                item.spread.inDisplayOrder(rightToLeft).forEach { pageIndex ->
                    val page = pages[pageIndex]
                    PageContent(
                        page = page,
                        isCurrent = state.settledPage == index,
                        scale = scale,
                        cache = cache,
                        zoom = true,
                        onTap = { x, width -> onTap(tapActionFor(x, width, mode)) },
                        onRetry = onRetry,
                        onSize = { sizes[pageIndex] = it },
                        onSave = onSave,
                        onCopy = onCopy,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
    if (mode.direction == ReadingMode.Direction.Vertical) {
        VerticalPager(state = state, modifier = pagerModifier, key = { items[it].key() }) { pageOf(it) }
    } else {
        HorizontalPager(state = state, modifier = pagerModifier, reverseLayout = rightToLeft, key = {
            items[it].key()
        }) {
            pageOf(it)
        }
    }
}

private fun ViewerItem.key(): Any = when (this) {
    is ViewerItem.Pages -> spread
    is ViewerItem.Transition -> transition.javaClass.simpleName
}

private class PagerController(
    private val state: PagerState,
    private val items: () -> List<ViewerItem>,
    private val onPastEnd: (forward: Boolean) -> Unit,
) : ViewerController {
    override suspend fun perform(action: ReaderAction) {
        val last = items().lastIndex
        // From the page a turn already in progress goes to, so two quick presses turn two pages
        when (action) {
            ReaderAction.NextPage, ReaderAction.ScrollDown ->
                if (state.targetPage < last) state.animateScrollToPage(state.targetPage + 1) else onPastEnd(true)
            ReaderAction.PreviousPage, ReaderAction.ScrollUp ->
                if (state.targetPage > 0) state.animateScrollToPage(state.targetPage - 1) else onPastEnd(false)
            ReaderAction.FirstPage -> state.scrollToPage(1.coerceAtMost(last))
            ReaderAction.LastPage -> state.scrollToPage((last - 1).coerceAtLeast(0))
            else -> Unit
        }
    }

    override suspend fun goToPage(index: Int) {
        state.scrollToPage(items().itemOfPage(index))
    }
}

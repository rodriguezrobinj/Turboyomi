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
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import mihon.desktop.app.reader.ImageScale
import mihon.desktop.app.reader.ReaderAction
import mihon.desktop.app.reader.Spread
import mihon.desktop.app.reader.image.DecodedPageCache
import mihon.desktop.app.reader.image.PageKey
import mihon.desktop.app.reader.image.pageKey
import mihon.desktop.app.reader.inDisplayOrder
import mihon.desktop.app.reader.pairPages
import mihon.desktop.app.reader.readingHalves
import mihon.desktop.app.reader.splitPages
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
    split: Boolean,
    splitInvert: Boolean,
    cache: DecodedPageCache,
    onPageSelected: (ReaderPage) -> Unit,
    onPastEnd: (forward: Boolean) -> Unit,
    onTap: (ReaderAction) -> Unit,
    onRetry: (ReaderPage) -> Unit,
    onController: (ViewerController) -> Unit,
    onSave: (ReaderPage) -> Unit,
    onCopy: (ReaderPage) -> Unit,
    jump: Int = 0,
) {
    val navigation = LocalViewerSettings.current.tapNavigation
    val current = chapters.currChapter
    val pages = current.pages.orEmpty()
    val sizes = remember { mutableStateMapOf<PageKey, IntSize>() }
    val zoomers = remember { mutableMapOf<PageKey, PageZoomer>() }
    val rightToLeft = mode == ReadingMode.RIGHT_TO_LEFT
    val isWide = { index: Int -> sizes[pages[index].pageKey]?.let { s -> s.width > s.height } ?: false }
    val spreads = when {
        dual && mode.direction == ReadingMode.Direction.Horizontal -> pairPages(pages.size, isWide, shifted)
        // With dual pages a wide page already shows alone (spec 8c)
        split -> splitPages(pages.size, isWide, readingHalves(rightToLeft, splitInvert))
        else -> pages.indices.map { Spread.Single(it) }
    }
    val items = rememberViewerItems(chapters, spreads)
    val latestItems by rememberUpdatedState(items)
    val latestCurrent by rememberUpdatedState(current)
    val state = rememberPagerState(initialPage = items.itemOfPage(current, current.requestedPage)) { latestItems.size }
    // Also when pairs change as page sizes become known: the page being read is no longer an item of its own
    FollowItemChanges(items, current, jump, shownIndex = { state.currentPage }) { state.requestScrollToPage(it) }

    LaunchedEffect(state) {
        snapshotFlow {
            // By key: the items may already be newer than the layout, and an index would point into them
            val key = state.layoutInfo.visiblePagesInfo.firstOrNull { it.index == state.settledPage }?.key
            latestItems.firstOrNull { it.key() == key } as? ViewerItem.Pages
        }.distinctUntilChanged().collect { item ->
            item ?: return@collect
            // The last page of a pair, so reaching the end of the chapter marks it read
            val page = item.pages.last()
            onPageSelected(page)
            cache.retainAround(page.chapter.chapter.id, page.index)
        }
    }
    val latestOnPastEnd by rememberUpdatedState(onPastEnd)
    val controller = remember(state) {
        PagerController(state, { latestItems }, { latestCurrent }, { zoomers.values.toList() }) { latestOnPastEnd(it) }
    }
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
                    detectTapGestures {
                        onTap(
                            tapActionFor(
                                it.x,
                                it.y,
                                size.width.toFloat(),
                                size.height.toFloat(),
                                mode,
                                navigation,
                            ),
                        )
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                ChapterTransition(item.transition, currChapterDownloaded = false, goingToChapterDownloaded = false)
            }
            is ViewerItem.Pages -> Row(Modifier.fillMaxSize()) {
                item.spread.inDisplayOrder(rightToLeft).forEach { pageIndex ->
                    val page = item.pages.first { it.index == pageIndex }
                    PageContent(
                        page = page,
                        isCurrent = state.settledPage == index,
                        half = (item.spread as? Spread.Single)?.half,
                        scale = scale,
                        cache = cache,
                        zoom = true,
                        onTap = { at, size ->
                            onTap(
                                tapActionFor(
                                    at.x,
                                    at.y,
                                    size.width.toFloat(),
                                    size.height.toFloat(),
                                    mode,
                                    navigation,
                                ),
                            )
                        },
                        onRetry = onRetry,
                        onSize = { sizes[page.pageKey] = it },
                        onSave = onSave,
                        onCopy = onCopy,
                        onZoomer = { zoomer ->
                            if (zoomer == null) zoomers.remove(page.pageKey) else zoomers[page.pageKey] = zoomer
                        },
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

private class PagerController(
    private val state: PagerState,
    private val items: () -> List<ViewerItem>,
    private val current: () -> ReaderChapter,
    private val zoomers: () -> List<PageZoomer>,
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
            ReaderAction.FirstPage -> state.scrollToPage(items().itemOfPage(current(), 0))
            ReaderAction.LastPage -> {
                val chapter = current()
                state.scrollToPage(
                    items().indexOfLast {
                        it is ViewerItem.Pages && it.chapter == chapter
                    }.coerceAtLeast(0),
                )
            }
            else -> Unit
        }
    }

    override suspend fun goToPage(index: Int) {
        state.scrollToPage(items().itemOfPage(current(), index))
    }

    // Both pages of a spread at once
    override suspend fun zoom(action: ReaderAction): Float? = zoomers().map { it.zoom(action) }.firstOrNull()
}

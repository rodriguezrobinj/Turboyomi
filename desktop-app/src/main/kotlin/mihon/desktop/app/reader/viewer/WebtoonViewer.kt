package mihon.desktop.app.reader.viewer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.reader.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import mihon.desktop.app.reader.ImageScale
import mihon.desktop.app.reader.ReaderAction
import mihon.desktop.app.reader.Spread
import mihon.desktop.app.reader.image.DecodedPageCache
import mihon.desktop.app.reader.image.PageKey
import mihon.desktop.app.reader.image.pageKey
import mihon.desktop.app.reader.tapActionFor
import kotlin.math.sign
import kotlin.time.Duration.Companion.seconds

const val WEBTOON_TAG = "webtoon"

/**
 * Webtoon (spec 2a §4): pages one under another with no space between them ([gap]: Mihon's continuous vertical, 16
 * dp), [sidePaddingPercent] of the width on each side. The neighbour chapters' pages follow on once they're loaded
 * (see [viewerItems]); going on past the end of the list (wheel, keys) loads the next chapter, and past the top the
 * previous one, and the list stays on the way across. [jump] changes when the reader asks for a chapter's page.
 */
@Composable
fun WebtoonViewer(
    chapters: ViewerChapters,
    gap: Boolean,
    sidePaddingPercent: Int,
    scrollStepPercent: Int,
    cache: DecodedPageCache,
    onPageSelected: (ReaderPage) -> Unit,
    onReachedEnd: () -> Unit,
    onReachedStart: () -> Unit,
    onTap: (ReaderAction) -> Unit,
    onRetry: (ReaderPage) -> Unit,
    onController: (ViewerController) -> Unit,
    onSave: (ReaderPage) -> Unit,
    onCopy: (ReaderPage) -> Unit,
    jump: Int = 0,
) {
    val current = chapters.currChapter
    val pages = current.pages.orEmpty()
    val spreads = remember(pages) { pages.indices.map { Spread.Single(it) } }
    val items = rememberViewerItems(chapters, spreads)
    // Decoded sizes: until a page has one it's as tall as the window, so a jump (End, resuming) lands on it
    // instead of on a page above that grows once it loads
    val sizes = remember { mutableStateMapOf<PageKey, IntSize>() }
    val latestItems by rememberUpdatedState(items)
    val latestCurrent by rememberUpdatedState(current)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = items.itemOfPage(current, current.requestedPage))
    FollowItemChanges(items, current, jump, shownIndex = { state.firstVisibleItemIndex }) {
        state.requestScrollToItem(it)
    }

    LaunchedEffect(state) {
        snapshotFlow {
            // The page at the top; at the bottom of the list, the last one in view, which may be too short to ever
            // reach the top and would leave the chapter unread. By key: the items may already be newer than the
            // layout, and an index would point into them
            val visible = state.layoutInfo.visibleItemsInfo
            val info = if (state.canScrollForward) {
                visible.firstOrNull()
            } else {
                visible.lastOrNull {
                    it.key is Pair<*, *>
                } // a page's key
            }
            latestItems.firstOrNull { it.key() == info?.key } as? ViewerItem.Pages
        }.distinctUntilChanged().collect { item ->
            val page = item?.pages?.single() ?: return@collect
            onPageSelected(page)
            cache.retainAround(page.chapter.chapter.id, page.index)
        }
    }
    val latestOnReachedEnd by rememberUpdatedState(onReachedEnd)
    val latestOnReachedStart by rememberUpdatedState(onReachedStart)
    val latestScrollStep by rememberUpdatedState(scrollStepPercent)
    val controller = remember(state) {
        WebtoonController(
            state = state,
            items = { latestItems },
            current = { latestCurrent },
            scrollStep = { latestScrollStep / 100f },
            isSized = { it.pageKey in sizes },
            onPastEnd = { latestOnReachedEnd() },
            onPastStart = { latestOnReachedStart() },
        )
    }
    LaunchedEffect(controller) { onController(controller) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val side = maxWidth * sidePaddingPercent / 100
        LazyColumn(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .testTag(WEBTOON_TAG)
                .onPointerEvent(PointerEventType.Scroll) { event ->
                    val delta = event.changes.first().scrollDelta.y
                    if (delta > 0 && !state.canScrollForward) latestOnReachedEnd()
                    if (delta < 0 && !state.canScrollBackward) latestOnReachedStart()
                },
            contentPadding = PaddingValues(horizontal = side),
            verticalArrangement = Arrangement.spacedBy(if (gap) 16.dp else 0.dp),
        ) {
            items(items, key = { it.key() }) { item ->
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
                        onSize = { sizes[item.pages.single().pageKey] = it },
                        onSave = onSave,
                        onCopy = onCopy,
                        // A page decoded before keeps its height while it's decoded again (it left the decoded
                        // cache, or the chapter it's in changed), else the list would jump as it grows back
                        modifier = sizes[item.pages.single().pageKey]?.let {
                            Modifier.fillMaxWidth().aspectRatio(it.width.toFloat() / it.height)
                        } ?: Modifier.fillMaxWidth().fillParentMaxHeight(),
                    )
                }
            }
        }
    }
}

private class WebtoonController(
    private val state: LazyListState,
    private val items: () -> List<ViewerItem>,
    private val current: () -> ReaderChapter,
    private val scrollStep: () -> Float,
    private val isSized: (ReaderPage) -> Boolean,
    private val onPastEnd: () -> Unit,
    private val onPastStart: () -> Unit,
) : ViewerController {
    override suspend fun perform(action: ReaderAction) {
        val viewport = state.layoutInfo.viewportSize.height.toFloat()
        val forward = action == ReaderAction.NextPage || action == ReaderAction.ScrollDown
        if (forward && !state.canScrollForward) return onPastEnd()
        val backward = action == ReaderAction.PreviousPage || action == ReaderAction.ScrollUp
        if (backward && !state.canScrollBackward) return onPastStart()
        when (action) {
            ReaderAction.NextPage -> glideBy(viewport * 0.9f)
            ReaderAction.PreviousPage -> glideBy(-viewport * 0.9f)
            ReaderAction.ScrollDown -> glideBy(viewport * scrollStep())
            ReaderAction.ScrollUp -> glideBy(-viewport * scrollStep())
            ReaderAction.FirstPage -> state.scrollToItem(items().itemOfPage(current(), 0))
            ReaderAction.LastPage -> scrollToLastPage()
            else -> Unit
        }
    }

    // One animation for every key press: a press while it runs moves its target and keeps its speed, so a held key
    // glides instead of starting again from rest on each repeat. A press the other way starts from rest.
    private val glide = Animatable(0f)
    private var glideTarget = 0f

    private suspend fun glideBy(delta: Float) {
        if (!glide.isRunning || sign(delta) != sign(glideTarget - glide.value)) {
            glide.snapTo(0f)
            glideTarget = 0f
        }
        glideTarget += delta
        val velocity = glide.velocity
        state.scroll {
            var scrolled = glide.value
            glide.animateTo(glideTarget, GlideSpec, initialVelocity = velocity) {
                scrollBy(value - scrolled)
                scrolled = value
            }
        }
    }

    override suspend fun goToPage(index: Int) {
        state.scrollToItem(items().itemOfPage(current(), index))
    }

    /**
     * Pages that come into view above a short last page grow when they load and push it out (the list keeps its first
     * visible item in place): once every page in view has its size, the jump is made again.
     * ponytail: bounded tries and wait; a page that keeps failing leaves the jump where it got.
     */
    private suspend fun scrollToLastPage() {
        val chapter = current()
        repeat(LAST_PAGE_TRIES) {
            // A move to another chapter while it waited (P, the chapter buttons) goes where that move went
            if (current() != chapter) return
            state.scrollToItem(items().indexOfLast { it is ViewerItem.Pages && it.chapter == chapter }.coerceAtLeast(0))
            if (visiblePagesSized()) return
            withTimeoutOrNull(5.seconds) { snapshotFlow { visiblePagesSized() }.first { it } } ?: return
            // The size is known a frame before the list lays the page out at it
            withFrameNanos { }
        }
    }

    private fun visiblePagesSized(): Boolean = state.layoutInfo.visibleItemsInfo.all { info ->
        val item = items().getOrNull(info.index) as? ViewerItem.Pages ?: return@all true
        isSized(item.pages.single())
    }
}

private const val LAST_PAGE_TRIES = 5

// Critically damped: follows a held key without overshooting the page
private val GlideSpec = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

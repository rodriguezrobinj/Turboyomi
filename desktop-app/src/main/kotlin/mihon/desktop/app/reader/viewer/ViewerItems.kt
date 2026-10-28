package mihon.desktop.app.reader.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import mihon.desktop.app.reader.ReaderAction
import mihon.desktop.app.reader.Spread
import mihon.desktop.app.reader.TapNavigation
import tachiyomi.domain.chapter.service.calculateChapterGap

/**
 * What the viewers show, in order (Mihon's adapters): the last pages of the previous chapter once it's loaded, the
 * way to it, the current chapter's pages (one or two per item), the way on and the first pages of the next chapter
 * once it's loaded. Reading on into a neighbour's page makes that chapter the current one.
 */
sealed interface ViewerItem {
    data class Pages(val spread: Spread, val pages: List<ReaderPage>) : ViewerItem {
        val chapter: ReaderChapter get() = pages.first().chapter
    }

    data class Transition(val transition: ChapterTransition) : ViewerItem
}

/** Mihon's: enough to see the way across, the neighbour becomes current before more would show. */
private const val NEIGHBOUR_PAGES = 2

/**
 * The items around the current chapter. Without [alwaysShowTransition] (Mihon's setting), a loaded neighbour with no
 * missing chapter in between runs straight on, with no way across between them (spec 8d).
 */
fun viewerItems(
    chapters: ViewerChapters,
    spreads: List<Spread>,
    alwaysShowTransition: Boolean = true,
): List<ViewerItem> {
    val current = chapters.currChapter
    val pages = current.pages.orEmpty()
    fun showsWayTo(neighbour: ReaderChapter?, higher: ReaderChapter?, lower: ReaderChapter?) =
        alwaysShowTransition ||
            neighbour?.pages == null ||
            calculateChapterGap(higher!!.chapter, lower!!.chapter) > 0
    return buildList {
        val prev = chapters.prevChapter
        val next = chapters.nextChapter
        prev?.pages?.takeLast(NEIGHBOUR_PAGES)?.forEach { add(it.single()) }
        if (showsWayTo(prev, current, prev)) add(ViewerItem.Transition(ChapterTransition.Prev(current, prev)))
        spreads.forEach { spread -> add(ViewerItem.Pages(spread, spread.pages.map(pages::get))) }
        if (showsWayTo(next, next, current)) add(ViewerItem.Transition(ChapterTransition.Next(current, next)))
        next?.pages?.take(NEIGHBOUR_PAGES)?.forEach { add(it.single()) }
    }
}

private fun ReaderPage.single() = ViewerItem.Pages(Spread.Single(index), listOf(this))

/** [viewerItems], again whenever a neighbour chapter finishes loading in the background. */
@Composable
fun rememberViewerItems(chapters: ViewerChapters, spreads: List<Spread>): List<ViewerItem> {
    val prev = chapters.prevChapter?.stateFlow?.collectAsState()?.value
    val next = chapters.nextChapter?.stateFlow?.collectAsState()?.value
    val always = LocalViewerSettings.current.alwaysShowTransition
    return remember(chapters, spreads, prev, next, always) { viewerItems(chapters, spreads, always) }
}

/** The reader's settings the viewers read (spec 8d): tap zones and whether chapters always get a way across. */
data class ViewerSettings(val tapNavigation: TapNavigation = TapNavigation(), val alwaysShowTransition: Boolean = true)

val LocalViewerSettings: ProvidableCompositionLocal<ViewerSettings> = staticCompositionLocalOf { ViewerSettings() }

/**
 * Stays the same while the chapters around it change, so the list keeps the item in view: a page by its chapter and
 * spread, a transition by the two chapters it joins (the next chapter's way back is the same place as this one's way
 * on).
 */
fun ViewerItem.key(): Any = when (this) {
    is ViewerItem.Pages -> Pair(chapter.chapter.id, spread)
    is ViewerItem.Transition -> transition.run {
        val to = to
        if (to == null) "${javaClass.simpleName} ${from.chapter.id}" else setOf(from.chapter.id, to.chapter.id)
    }
}

/** The item showing page [index] of [chapter], else its first page, else the way into it. */
fun List<ViewerItem>.itemOfPage(chapter: ReaderChapter, index: Int): Int {
    fun ViewerItem.isOf(chapter: ReaderChapter) = this is ViewerItem.Pages && this.chapter == chapter
    return indexOfFirst { it.isOf(chapter) && (it as ViewerItem.Pages).spread.pages.contains(index) }
        .takeIf { it >= 0 }
        ?: indexOfFirst { it.isOf(chapter) }.takeIf { it >= 0 }
        ?: indexOfFirst { it is ViewerItem.Transition }.coerceAtLeast(0)
}

/**
 * Where the list goes when its items change (called during composition, with the position still on the old items):
 * nowhere while the item in view is still there, since the list keeps it by its key; to [chapter]'s requested page
 * when that item is gone (its chapter dropped out) or when the reader asked to [jump] to the chapter.
 */
fun scrollTargetAfterChange(
    old: List<ViewerItem>,
    new: List<ViewerItem>,
    shownIndex: Int,
    chapter: ReaderChapter,
    jump: Boolean,
): Int? {
    val shownItem = old.getOrNull(shownIndex)
    val shown = shownItem?.key()
    if (!jump && new.any { it.key() == shown }) return null
    // The item shown changed shape (paired, or split in halves, once its size is known): stay on its first page
    if (!jump && shownItem is ViewerItem.Pages) {
        val page = shownItem.pages.first()
        new.indexOfFirst { item ->
            item is ViewerItem.Pages && item.pages.any { it.chapter == page.chapter && it.index == page.index }
        }.takeIf { it >= 0 }?.let { return it }
    }
    return new.itemOfPage(chapter, chapter.requestedPage)
}

/**
 * Moves the list, during composition, when its items or [jump] change: [shownIndex] is still the position on the old
 * items then, and [scrollTo] (requestScrollToItem/Page) applies on the next layout, before anything sees the page
 * the list would otherwise show at that index.
 */
@Composable
fun FollowItemChanges(
    items: List<ViewerItem>,
    chapter: ReaderChapter,
    jump: Int,
    shownIndex: () -> Int,
    scrollTo: (Int) -> Unit,
) {
    val last = remember { LastLaidOut(items, jump) }
    if (last.items === items && last.jump == jump) return
    val shown = Snapshot.withoutReadObservation(shownIndex)
    scrollTargetAfterChange(last.items, items, shown, chapter, jump = last.jump != jump)?.let(scrollTo)
    last.items = items
    last.jump = jump
}

private class LastLaidOut(var items: List<ViewerItem>, var jump: Int)

/** Turns, jumps and scrolls for the viewer on screen; the reader screen sends the keys and clicks here. */
interface ViewerController {
    suspend fun perform(action: ReaderAction)

    suspend fun goToPage(index: Int)

    /**
     * Scrolls by itself, [speed] of the window's height per second read on every frame, until cancelled or stuck
     * at the end. Webtoon only; the paged viewers do nothing.
     */
    suspend fun autoScroll(speed: () -> Float) {}

    /** Paged: zooms the page or pages on screen and returns the new scale; null where there's nothing to zoom. */
    suspend fun zoom(action: ReaderAction): Float? = null
}

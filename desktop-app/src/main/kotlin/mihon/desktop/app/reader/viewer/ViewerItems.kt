package mihon.desktop.app.reader.viewer

import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import mihon.desktop.app.reader.ReaderAction
import mihon.desktop.app.reader.Spread

/** What the viewers show, in order: the way to the previous chapter, the pages (one or two per item), the way on. */
sealed interface ViewerItem {
    data class Pages(val spread: Spread, val pages: List<ReaderPage>) : ViewerItem

    data class Transition(val transition: ChapterTransition) : ViewerItem
}

fun viewerItems(chapters: ViewerChapters, spreads: List<Spread>): List<ViewerItem> {
    val current = chapters.currChapter
    val pages = current.pages.orEmpty()
    return buildList {
        add(ViewerItem.Transition(ChapterTransition.Prev(current, chapters.prevChapter)))
        spreads.forEach { spread -> add(ViewerItem.Pages(spread, spread.pages.map(pages::get))) }
        add(ViewerItem.Transition(ChapterTransition.Next(current, chapters.nextChapter)))
    }
}

/** The item showing page [index] of the current chapter, or the first page's when there's none. */
fun List<ViewerItem>.itemOfPage(index: Int): Int =
    indexOfFirst { it is ViewerItem.Pages && it.spread.pages.contains(index) }.takeIf { it >= 0 } ?: 1

/** Turns, jumps and scrolls for the viewer on screen; the reader screen sends the keys and clicks here. */
interface ViewerController {
    suspend fun perform(action: ReaderAction)

    suspend fun goToPage(index: Int)
}

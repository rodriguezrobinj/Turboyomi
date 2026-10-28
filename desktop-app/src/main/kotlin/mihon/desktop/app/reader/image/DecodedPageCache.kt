package mihon.desktop.app.reader.image

import eu.kanade.tachiyomi.ui.reader.model.ReaderPage

data class PageKey(val chapterId: Long, val index: Int)

val ReaderPage.pageKey: PageKey get() = PageKey(chapter.chapter.id, index)

/**
 * The decoded pages kept in memory (spec 2a §4): from [behind] before to [ahead] after the page being read. The
 * others stay in the chapter cache as bytes and are decoded again if the reader comes back to them.
 */
class DecodedPageCache(private val behind: Int = 2, private val ahead: Int = 4) {
    private val pages = HashMap<PageKey, DecodedPage>()

    val size: Int
        @Synchronized get() = pages.size

    @Synchronized
    fun get(key: PageKey): DecodedPage? = pages[key]

    @Synchronized
    fun put(key: PageKey, page: DecodedPage) {
        pages[key] = page
    }

    /** Drops every page outside the window around [current] of [chapterId]; returns the ones dropped. */
    @Synchronized
    fun retainAround(chapterId: Long, current: Int): Set<PageKey> {
        val window = (current - behind)..(current + ahead)
        val dropped = pages.keys.filterTo(HashSet()) { it.chapterId != chapterId || it.index !in window }
        dropped.forEach(pages::remove)
        return dropped
    }
}

package mihon.desktop.app.reader.image

import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlin.math.abs

data class PageKey(val chapterId: Long, val index: Int)

val ReaderPage.pageKey: PageKey get() = PageKey(chapter.chapter.id, index)

/**
 * The decoded pages kept in memory (spec 2a §4): from [behind] before to [ahead] after the page being read, and at
 * most [maxBytes] of them. The others stay in the chapter cache as bytes and are decoded again if the reader comes
 * back to them. It owns every page it's given and closes it once it's dropped and no page on screen draws it
 * (spec 10a): closing a bitmap Skia is drawing breaks it.
 */
class DecodedPageCache(
    private val behind: Int = 2,
    private val ahead: Int = 4,
    private val maxBytes: Long = MAX_BYTES,
) : AutoCloseable {
    private val pages = HashMap<PageKey, DecodedPage>()

    // Dropped while still on screen: closed when hidden
    private val dropped = HashMap<PageKey, DecodedPage>()
    private val shown = HashMap<PageKey, Int>()
    private var closed = false

    val size: Int
        @Synchronized get() = pages.size

    @Synchronized
    fun get(key: PageKey): DecodedPage? {
        pages[key]?.let { return it }
        // Kept again rather than closed: Compose hides an old page before it shows its replacement
        return if (closed) dropped[key] else dropped.remove(key)?.also { pages[key] = it }
    }

    /** Takes [page]; returns the one to draw, which is the one it had already if both were decoded at once. */
    @Synchronized
    fun put(key: PageKey, page: DecodedPage): DecodedPage {
        get(key)?.let {
            page.close()
            return it
        }
        pages[key] = page
        if (closed) drop(key)
        return page
    }

    /** A page on screen draws [key]'s page from now until [hide]. */
    @Synchronized
    fun show(key: PageKey) {
        shown.merge(key, 1, Int::plus)
    }

    @Synchronized
    fun hide(key: PageKey) {
        val count = (shown[key] ?: return) - 1
        if (count > 0) {
            shown[key] = count
        } else {
            shown.remove(key)
            dropped.remove(key)?.close()
        }
    }

    /** Drops every page outside the window around [current] of [chapterId], then the farthest while over budget. */
    @Synchronized
    fun retainAround(chapterId: Long, current: Int) {
        val window = (current - behind)..(current + ahead)
        pages.keys.filter { it.chapterId != chapterId || it.index !in window }.forEach(::drop)
        var total = 0L
        pages.keys.sortedBy { abs(it.index - current) }.forEach { key ->
            total += pages.getValue(key).bytes
            if (total > maxBytes && key.index != current) drop(key)
        }
    }

    /** The reader is done with it: closes what isn't on screen, and the rest as it's hidden. */
    @Synchronized
    override fun close() {
        closed = true
        pages.keys.toList().forEach(::drop)
    }

    private fun drop(key: PageKey) {
        val page = pages.remove(key) ?: return
        if (key in shown) dropped[key] = page else page.close()
    }

    private companion object {
        const val MAX_BYTES = 512L shl 20
    }
}

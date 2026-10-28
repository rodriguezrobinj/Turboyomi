package mihon.desktop.app.reader.viewer

import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import io.kotest.matchers.shouldBe
import mihon.desktop.app.reader.Spread
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter

class ViewerItemsTest {
    private fun chapter(id: Long, pages: Int? = null) = ReaderChapter(Chapter.create().copy(id = id)).apply {
        if (pages != null) {
            state = ReaderChapter.State.Loaded(List(pages) { i -> ReaderPage(i).also { it.chapter = this } })
        }
    }

    private fun items(chapters: ViewerChapters) =
        viewerItems(chapters, chapters.currChapter.pages!!.indices.map { Spread.Single(it) })

    private fun List<ViewerItem>.describe() = map {
        when (it) {
            is ViewerItem.Pages -> "${it.chapter.chapter.id}/${it.pages.single().index}"
            is ViewerItem.Transition -> "|"
        }
    }

    @Test
    fun `loaded neighbours show their pages next to the way across, the others only the way`() {
        val one = chapter(1, pages = 4)
        val two = chapter(2, pages = 3)
        val three = chapter(3)

        items(ViewerChapters(two, one, three)).describe() shouldBe listOf("1/2", "1/3", "|", "2/0", "2/1", "2/2", "|")
        items(ViewerChapters(one, null, two)).describe() shouldBe
            listOf("|", "1/0", "1/1", "1/2", "1/3", "|", "2/0", "2/1")
    }

    @Test
    fun `reading on into the next chapter keeps the item in view where it is`() {
        val one = chapter(1, pages = 4)
        val two = chapter(2, pages = 3)
        val before = items(ViewerChapters(one, null, two))
        val after = items(ViewerChapters(two, one, chapter(3)))

        // The last two pages, the way across and the next chapter's first two are the same items before and after
        before.map { it.key() }.filter { it in after.map { a -> a.key() } }.size shouldBe 5
        scrollTargetAfterChange(before, after, shownIndex = 6, chapter = two, jump = false) shouldBe null
        scrollTargetAfterChange(before, after, shownIndex = 5, chapter = two, jump = false) shouldBe null
    }

    @Test
    fun `a page that dropped out of the list, or a jump, goes to the chapter's requested page`() {
        val one = chapter(1, pages = 4)
        val two = chapter(2, pages = 3).apply { requestedPage = 1 }
        val before = items(ViewerChapters(one, null, two))
        val after = items(ViewerChapters(two, one, chapter(3)))

        // Page 1/0 isn't among the previous chapter's last pages any more
        scrollTargetAfterChange(before, after, shownIndex = 1, chapter = two, jump = false) shouldBe 4
        scrollTargetAfterChange(before, after, shownIndex = 6, chapter = two, jump = true) shouldBe 4
    }
}

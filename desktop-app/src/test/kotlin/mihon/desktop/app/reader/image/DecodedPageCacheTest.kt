package mihon.desktop.app.reader.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.jupiter.api.Test

class DecodedPageCacheTest {
    // 5×5 px: 100 bytes
    private fun page() = DecodedPage.Still(5, 5, listOf(ImageBitmap(5, 5)))

    private val DecodedPage.isClosed: Boolean get() = (this as DecodedPage.Still).tiles.single().asSkiaBitmap().isClosed

    @Test
    fun `only the pages from two before to four after the current one stay decoded, and the others are closed`() {
        val cache = DecodedPageCache()
        val pages = (0..20).associateWith { page().also { p -> cache.put(PageKey(1, it), p) } }
        val other = page().also { cache.put(PageKey(2, 5), it) }

        cache.retainAround(chapterId = 1, current = 10)

        cache.size shouldBe 7
        (8..14).forEach { cache.get(PageKey(1, it)).shouldNotBeNull() }
        cache.get(PageKey(1, 7)).shouldBeNull()
        cache.get(PageKey(1, 15)).shouldBeNull()
        // Pages of another chapter go too
        cache.get(PageKey(2, 5)).shouldBeNull()
        pages.getValue(7).isClosed shouldBe true
        pages.getValue(10).isClosed shouldBe false
        other.isClosed shouldBe true
    }

    @Test
    fun `turning many pages keeps the cache bounded`() {
        val cache = DecodedPageCache()
        (0 until 200).forEach { current ->
            cache.put(PageKey(1, current), page())
            cache.retainAround(chapterId = 1, current = current)
        }
        cache.size shouldBe 3
    }

    @Test
    fun `a page dropped while on screen is closed only once it's hidden, and can be kept again before that`() {
        val cache = DecodedPageCache()
        val shown = page()
        val key = PageKey(1, 0)
        cache.put(key, shown)
        cache.show(key)

        cache.retainAround(chapterId = 1, current = 50)
        shown.isClosed shouldBe false
        // Compose hides an old page before it shows its replacement: the replacement gets it back
        cache.get(key) shouldBeSameInstanceAs shown
        cache.retainAround(chapterId = 1, current = 50)

        cache.hide(key)
        shown.isClosed shouldBe true
        cache.get(key).shouldBeNull()
    }

    @Test
    fun `a page decoded twice at once keeps the first and closes the second`() {
        val cache = DecodedPageCache()
        val first = page()
        val second = page()
        cache.put(PageKey(1, 0), first) shouldBeSameInstanceAs first
        cache.put(PageKey(1, 0), second) shouldBeSameInstanceAs first
        second.isClosed shouldBe true
        first.isClosed shouldBe false
    }

    @Test
    fun `over budget the farthest pages go first, never the current one`() {
        val cache = DecodedPageCache(maxBytes = 250)
        val pages = (0..6).associateWith { page().also { p -> cache.put(PageKey(1, it), p) } }

        cache.retainAround(chapterId = 1, current = 2)

        // 250 bytes: the current page and one of its neighbours
        cache.size shouldBe 2
        cache.get(PageKey(1, 2)).shouldNotBeNull()
        pages.getValue(6).isClosed shouldBe true

        val tiny = DecodedPageCache(maxBytes = 10)
        val current = page().also { tiny.put(PageKey(1, 0), it) }
        tiny.retainAround(chapterId = 1, current = 0)
        current.isClosed shouldBe false
    }

    @Test
    fun `closing the cache closes what isn't on screen, and the rest once hidden`() {
        val cache = DecodedPageCache()
        val hidden = page().also { cache.put(PageKey(1, 0), it) }
        val shown = page().also { cache.put(PageKey(1, 1), it) }
        cache.show(PageKey(1, 1))

        cache.close()
        hidden.isClosed shouldBe true
        shown.isClosed shouldBe false

        // A decode that ends after the reader left
        val late = page()
        cache.show(PageKey(1, 2))
        cache.put(PageKey(1, 2), late)
        cache.size shouldBe 0

        cache.hide(PageKey(1, 1))
        cache.hide(PageKey(1, 2))
        shown.isClosed shouldBe true
        late.isClosed shouldBe true
    }
}

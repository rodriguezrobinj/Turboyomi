package mihon.desktop.app.reader.image

import androidx.compose.ui.graphics.ImageBitmap
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DecodedPageCacheTest {
    private val page = DecodedPage.Still(1, 1, listOf(ImageBitmap(1, 1)))

    @Test
    fun `only the pages from two before to four after the current one stay decoded`() {
        val cache = DecodedPageCache()
        (0..20).forEach { cache.put(PageKey(1, it), page) }
        cache.put(PageKey(2, 5), page)

        val dropped = cache.retainAround(chapterId = 1, current = 10)

        cache.size shouldBe 7
        (8..14).forEach { cache.get(PageKey(1, it)).shouldNotBeNull() }
        cache.get(PageKey(1, 7)).shouldBeNull()
        cache.get(PageKey(1, 15)).shouldBeNull()
        // Pages of another chapter go too
        cache.get(PageKey(2, 5)).shouldBeNull()
        dropped.size shouldBe 15
    }

    @Test
    fun `turning many pages keeps the cache bounded`() {
        val cache = DecodedPageCache()
        (0 until 200).forEach { current ->
            cache.put(PageKey(1, current), page)
            cache.retainAround(chapterId = 1, current = current)
        }
        cache.size shouldBe 3
    }
}

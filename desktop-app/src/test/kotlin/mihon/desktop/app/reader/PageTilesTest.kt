package mihon.desktop.app.reader

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PageTilesTest {
    @Test
    fun `a page under the limit is one tile`() {
        tileRanges(1200) shouldBe listOf(0 until 1200)
        tileRanges(TILE_LIMIT) shouldBe listOf(0 until TILE_LIMIT)
    }

    @Test
    fun `a 30000 px page is cut in contiguous tiles of at most 4096 px that add up to its height`() {
        val tiles = tileRanges(30_000)
        tiles shouldHaveSize 8
        tiles.forEach { it.count() shouldBeLessThanOrEqual TILE_HEIGHT }
        tiles.sumOf { it.count() } shouldBe 30_000
        tiles.zipWithNext().forEach { (a, b) -> b.first shouldBe a.last + 1 }
        tiles.first().first shouldBe 0
        tiles.last().last shouldBe 29_999
    }
}

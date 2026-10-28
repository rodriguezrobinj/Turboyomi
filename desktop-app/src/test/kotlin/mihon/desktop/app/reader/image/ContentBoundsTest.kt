package mihon.desktop.app.reader.image

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ContentBoundsTest {
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()
    private val ink = 0xFF336699.toInt()

    // A width × height page of [border] with an [ink] block from (l, t) to (r, b), exclusive
    private fun page(width: Int, height: Int, border: Int, l: Int, t: Int, r: Int, b: Int) = { x: Int, y: Int ->
        if (x in l until r && y in t until b) ink else border
    }

    @Test
    fun `white and black margins are cropped`() {
        contentBounds(100, 200, page(100, 200, white, 10, 20, 90, 180)) shouldBe CropRect(10, 20, 90, 180)
        contentBounds(100, 200, page(100, 200, black, 5, 5, 95, 195)) shouldBe CropRect(5, 5, 95, 195)
    }

    @Test
    fun `a page without margins, a nearly empty one and a colored border stay whole`() {
        contentBounds(100, 200, page(100, 200, white, 0, 0, 100, 200)) shouldBe CropRect(0, 0, 100, 200)
        contentBounds(100, 200, page(100, 200, white, 45, 95, 55, 105)) shouldBe CropRect(0, 0, 100, 200)
        val red = 0xFFCC2222.toInt()
        contentBounds(100, 200, page(100, 200, red, 10, 20, 90, 180)) shouldBe CropRect(0, 0, 100, 200)
    }
}

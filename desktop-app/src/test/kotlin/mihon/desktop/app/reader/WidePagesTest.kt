package mihon.desktop.app.reader

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class WidePagesTest {
    @Test
    fun `halves follow the reading direction, or the other way when inverted`() {
        readingHalves(rightToLeft = false, invert = false) shouldBe listOf(Half.Left, Half.Right)
        readingHalves(rightToLeft = true, invert = false) shouldBe listOf(Half.Right, Half.Left)
        readingHalves(rightToLeft = false, invert = true) shouldBe listOf(Half.Right, Half.Left)
        readingHalves(rightToLeft = true, invert = true) shouldBe listOf(Half.Left, Half.Right)
    }

    @Test
    fun `a wide page becomes two spreads, one per half`() {
        splitPages(3, isWide = { it == 1 }, halves = listOf(Half.Right, Half.Left)) shouldBe listOf(
            Spread.Single(0),
            Spread.Single(1, Half.Right),
            Spread.Single(1, Half.Left),
            Spread.Single(2),
        )
    }

    @Test
    fun `only wide pages turn, the other way when inverted`() {
        wideRotation(1200, 600, rotate = true, invert = false) shouldBe 90
        wideRotation(1200, 600, rotate = true, invert = true) shouldBe -90
        wideRotation(600, 900, rotate = true, invert = false) shouldBe 0
        wideRotation(1200, 600, rotate = false, invert = false) shouldBe 0
    }
}

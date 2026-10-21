package mihon.desktop.app.reader

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DualPageTest {
    private val portrait: (Int) -> Boolean = { false }

    @Test
    fun `an even number of pages pairs them all`() {
        pairPages(4, portrait, shifted = false) shouldBe listOf(Spread.Double(0, 1), Spread.Double(2, 3))
    }

    @Test
    fun `an odd number of pages leaves the last one alone`() {
        pairPages(5, portrait, shifted = false) shouldBe
            listOf(Spread.Double(0, 1), Spread.Double(2, 3), Spread.Single(4))
    }

    @Test
    fun `a page wider than tall shows alone and pairing restarts after it`() {
        pairPages(5, { it == 2 }, shifted = false) shouldBe
            listOf(Spread.Double(0, 1), Spread.Single(2), Spread.Double(3, 4))
        pairPages(4, { it == 1 }, shifted = false) shouldBe
            listOf(Spread.Single(0), Spread.Single(1), Spread.Double(2, 3))
    }

    @Test
    fun `shifting by one shows the first page alone and moves the pairs`() {
        pairPages(5, portrait, shifted = true) shouldBe
            listOf(Spread.Single(0), Spread.Double(1, 2), Spread.Double(3, 4))
    }

    @Test
    fun `right to left shows the first page of a pair on the right`() {
        Spread.Double(2, 3).inDisplayOrder(rightToLeft = false) shouldBe listOf(2, 3)
        Spread.Double(2, 3).inDisplayOrder(rightToLeft = true) shouldBe listOf(3, 2)
        Spread.Single(4).inDisplayOrder(rightToLeft = true) shouldBe listOf(4)
    }
}

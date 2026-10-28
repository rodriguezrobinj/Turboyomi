package mihon.desktop.app.reader

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ReaderColorFilterTest {
    @Test
    fun `no filter without grayscale or inverted colors`() {
        readerColorMatrix(grayscale = false, inverted = false) shouldBe null
    }

    @Test
    fun `inverted colors flip each channel`() {
        val m = readerColorMatrix(grayscale = false, inverted = true).shouldNotBeNull().values
        m[0] shouldBe -1f
        m[4] shouldBe 255f
        m[6] shouldBe -1f
        m[12] shouldBe -1f
        m[18] shouldBe 1f
    }

    @Test
    fun `grayscale gives the three channels the same mix, and goes with inverted colors`() {
        val gray = readerColorMatrix(grayscale = true, inverted = false).shouldNotBeNull().values
        gray.copyOfRange(0, 3).toList() shouldBe gray.copyOfRange(5, 8).toList()
        gray.copyOfRange(0, 3).toList() shouldBe gray.copyOfRange(10, 13).toList()
        val both = readerColorMatrix(grayscale = true, inverted = true).shouldNotBeNull().values
        both.copyOfRange(0, 3).forEach { (it < 0f) shouldBe true }
        both[4] shouldBe 255f
    }
}

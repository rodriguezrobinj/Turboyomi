package mihon.desktop.app.reader.image

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class PageDecoderTest {
    private fun png(width: Int, height: Int, format: String = "png"): ByteArray = ByteArrayOutputStream().also {
        ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, it)
    }.toByteArray()

    @Test
    fun `a normal page is one bitmap`() {
        val page = PageDecoder.decode(png(600, 900)).shouldBeInstanceOf<DecodedPage.Still>()
        page.width shouldBe 600
        page.height shouldBe 900
        page.tiles shouldHaveSize 1
    }

    @Test
    fun `a very tall page is decoded in strips that add up to its height`() {
        val page = PageDecoder.decode(png(400, 30_000)).shouldBeInstanceOf<DecodedPage.Still>()
        page.tiles shouldHaveSize 8
        page.tiles.sumOf { it.height } shouldBe 30_000
        page.tiles.forEach { it.width shouldBe 400 }
    }

    @Test
    fun `jpeg and bmp decode too`() {
        PageDecoder.decode(png(300, 400, "jpg")).height shouldBe 400
        PageDecoder.decode(png(300, 400, "bmp")).height shouldBe 400
    }

    @Test
    fun `bytes that aren't an image report an error instead of crashing`() {
        shouldThrow<UnreadableImageException> { PageDecoder.decode("no es una imagen".toByteArray()) }
    }
}

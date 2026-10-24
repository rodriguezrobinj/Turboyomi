package mihon.desktop.app.packaging

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO

class IconRendererTest {
    @Test
    fun `the ico holds the six sizes, each a png of its size with the icon drawn`() {
        val svg = javaClass.getResourceAsStream("/turboyomi.svg")!!.readBytes()
        val sizes = listOf(16, 24, 32, 48, 64, 256)

        val ico = IconRenderer.ico(svg, sizes)

        val header = ByteBuffer.wrap(ico).order(ByteOrder.LITTLE_ENDIAN)
        header.getShort(2).toInt() shouldBe 1 // icon
        header.getShort(4).toInt() shouldBe sizes.size
        sizes.forEachIndexed { i, size ->
            val entry = 6 + i * 16
            val length = header.getInt(entry + 8)
            val offset = header.getInt(entry + 12)
            val image = ImageIO.read(ico.copyOfRange(offset, offset + length).inputStream())
            image.width shouldBe size
            image.height shouldBe size
            // The background fills the middle at every size: the SVG was scaled, not cropped to its corner
            (image.getRGB(size / 2, size / 8) ushr 24) shouldBe 0xFF
        }
    }
}

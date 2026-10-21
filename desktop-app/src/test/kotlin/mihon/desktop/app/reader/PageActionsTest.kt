package mihon.desktop.app.reader

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class PageActionsTest {
    private fun image(format: String): ByteArray = ByteArrayOutputStream().also {
        ImageIO.write(BufferedImage(30, 40, BufferedImage.TYPE_INT_RGB), format, it)
    }.toByteArray()

    @Test
    fun `any page image becomes a PNG the clipboard can take`() {
        ImageIO.read(PageActions.toPng(image("jpg")).inputStream()).shouldNotBeNull().height shouldBe 40
    }

    @Test
    fun `the saved file keeps the image's real format`() {
        PageActions.extensionOf(image("png")) shouldBe "png"
        PageActions.extensionOf(image("jpg")) shouldBe "jpg"
        PageActions.extensionOf("?".toByteArray()) shouldBe "png"
    }
}

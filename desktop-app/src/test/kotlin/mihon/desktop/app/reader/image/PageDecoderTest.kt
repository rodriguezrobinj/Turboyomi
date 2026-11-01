package mihon.desktop.app.reader.image

import androidx.compose.ui.graphics.asSkiaBitmap
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.ImageWriter
import javax.imageio.metadata.IIOMetadata
import javax.imageio.metadata.IIOMetadataNode

class PageDecoderTest {
    private fun png(
        width: Int,
        height: Int,
        format: String = "png",
        type: Int = BufferedImage.TYPE_INT_RGB,
    ): ByteArray = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(width, height, type), format, it) }
        .toByteArray()

    // A white page with a dark block in the middle: margins of 20 px left and right, 30 px top and bottom
    private fun pageWithMargins(): ByteArray = ByteArrayOutputStream().also {
        val image = BufferedImage(200, 300, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until 200) {
            for (y in 0 until 300) {
                image.setRGB(x, y, if (x in 20 until 180 && y in 30 until 270) 0x336699 else 0xFFFFFF)
            }
        }
        ImageIO.write(image, "png", it)
    }.toByteArray()

    @Test
    fun `a wide page turned to fit comes out tall, and the margins can be cropped`() {
        val turned = PageDecoder.decode(png(300, 100), DecodeOptions(rotateWide = true))
        turned.width shouldBe 100
        turned.height shouldBe 300
        // A page that's already tall stays as it is
        PageDecoder.decode(png(100, 300), DecodeOptions(rotateWide = true)).width shouldBe 100

        val cropped = PageDecoder.decode(pageWithMargins(), DecodeOptions(crop = true))
        cropped.width shouldBe 160
        cropped.height shouldBe 240
        PageDecoder.decode(pageWithMargins()).width shouldBe 200
    }

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

    @Test
    fun `an image too big to decode reports an error before taking its memory`() {
        // 156 Mpx, 600 MB once decoded; one bit per pixel and compressed here
        val huge = png(13_000, 12_000, type = BufferedImage.TYPE_BYTE_BINARY)
        shouldThrow<UnreadableImageException> { PageDecoder.decode(huge) }
    }

    @Test
    fun `an animation plays, unless keeping all its frames would take too much memory`() {
        PageDecoder.decode(gif(10, 10, frames = 3)).shouldBeInstanceOf<DecodedPage.Animated>().frames shouldHaveSize 3
        // 17 frames of 2000×2000: 272 MB
        val still = PageDecoder.decode(gif(2000, 2000, frames = 17)).shouldBeInstanceOf<DecodedPage.Still>()
        still.width shouldBe 2000
    }

    @Test
    fun `a frame that only redraws part of the picture keeps the rest of the frame before it`() {
        val red = BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB).apply {
            for (x in 0 until 10) for (y in 0 until 10) setRGB(x, y, 0xFF0000)
        }
        val blue = BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB).apply {
            for (x in 0 until 2) for (y in 0 until 2) setRGB(x, y, 0x0000FF)
        }
        val gif = ByteArrayOutputStream().also { out ->
            val writer = ImageIO.getImageWritersByFormatName("gif").next()
            ImageIO.createImageOutputStream(out).use { stream ->
                writer.output = stream
                writer.prepareWriteSequence(null)
                // Each frame left in place for the next, which only draws its 2×2 patch at (4, 4)
                listOf(red to 0, blue to 4, blue to 6).forEach { (image, at) ->
                    writer.writeToSequence(IIOImage(image, null, kept(writer, image, at)), null)
                }
                writer.endWriteSequence()
            }
            writer.dispose()
        }.toByteArray()

        val page = PageDecoder.decode(gif).shouldBeInstanceOf<DecodedPage.Animated>()
        val last = page.frames.last().asSkiaBitmap()
        last.getColor(0, 0) shouldBe 0xFFFF0000.toInt()
        last.getColor(4, 4) shouldBe 0xFF0000FF.toInt()
        last.getColor(6, 6) shouldBe 0xFF0000FF.toInt()
    }

    // A GIF frame drawn at (at, at) and not disposed of, so the next frame draws over it
    private fun kept(writer: ImageWriter, image: BufferedImage, at: Int): IIOMetadata =
        writer.getDefaultImageMetadata(ImageTypeSpecifier(image), null).apply {
            val format = nativeMetadataFormatName
            val root = getAsTree(format) as IIOMetadataNode
            (root.getElementsByTagName("ImageDescriptor").item(0) as IIOMetadataNode).apply {
                setAttribute("imageLeftPosition", "$at")
                setAttribute("imageTopPosition", "$at")
            }
            val control = root.getElementsByTagName("GraphicControlExtension").item(0) as? IIOMetadataNode
                ?: IIOMetadataNode("GraphicControlExtension").also(root::appendChild)
            control.setAttribute("disposalMethod", "doNotDispose")
            control.setAttribute("userInputFlag", "FALSE")
            control.setAttribute("transparentColorFlag", "FALSE")
            control.setAttribute("delayTime", "10")
            control.setAttribute("transparentColorIndex", "0")
            setFromTree(format, root)
        }

    private fun gif(width: Int, height: Int, frames: Int): ByteArray = ByteArrayOutputStream().also { out ->
        val writer = ImageIO.getImageWritersByFormatName("gif").next()
        ImageIO.createImageOutputStream(out).use { stream ->
            writer.output = stream
            writer.prepareWriteSequence(null)
            val frame = IIOImage(BufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED), null, null)
            repeat(frames) { writer.writeToSequence(frame, null) }
            writer.endWriteSequence()
        }
        writer.dispose()
    }.toByteArray()
}

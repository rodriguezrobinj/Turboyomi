package mihon.desktop.app.reader

import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/** Right click on a page (spec 2a §4): save it as it came, or copy it as an image. */
object PageActions {
    /** The page as PNG: AWT can't read WebP, Skia can. */
    fun toPng(bytes: ByteArray): ByteArray = Image.makeFromEncoded(bytes).use { image ->
        image.encodeToData(EncodedImageFormat.PNG)?.bytes ?: error("PNG encoding failed")
    }

    fun copyImage(bytes: ByteArray) {
        val image = ImageIO.read(toPng(bytes).inputStream()) ?: error("Not an image")
        Toolkit.getDefaultToolkit().systemClipboard.setContents(ImageSelection(image), null)
    }

    /** The extension to save the page with, from the file's signature. */
    fun extensionOf(bytes: ByteArray): String = when {
        bytes.startsWith(0xFF, 0xD8) -> "jpg"
        bytes.startsWith('G'.code, 'I'.code, 'F'.code) -> "gif"
        bytes.size > 12 && String(bytes, 8, 4) == "WEBP" -> "webp"
        bytes.startsWith('B'.code, 'M'.code) -> "bmp"
        else -> "png"
    }

    private fun ByteArray.startsWith(vararg values: Int) =
        size >= values.size && values.indices.all { this[it] == values[it].toByte() }

    private class ImageSelection(private val image: BufferedImage) : Transferable {
        override fun getTransferDataFlavors() = arrayOf(DataFlavor.imageFlavor)
        override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DataFlavor.imageFlavor
        override fun getTransferData(flavor: DataFlavor): Any =
            if (flavor == DataFlavor.imageFlavor) image else throw UnsupportedFlavorException(flavor)
    }
}

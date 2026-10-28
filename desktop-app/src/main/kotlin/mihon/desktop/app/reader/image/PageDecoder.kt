package mihon.desktop.app.reader.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import mihon.desktop.app.reader.tileRanges
import mihon.desktop.app.reader.wideRotation
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.IRect
import org.jetbrains.skia.Image

/** A page ready to draw: strips of a still image (one, unless it's very tall), or the frames of an animated GIF. */
sealed interface DecodedPage {
    val width: Int
    val height: Int

    class Still(override val width: Int, override val height: Int, val tiles: List<ImageBitmap>) : DecodedPage

    class Animated(
        override val width: Int,
        override val height: Int,
        val frames: List<ImageBitmap>,
        val durationsMs: List<Int>,
    ) : DecodedPage
}

/** What to do to a still page as it's decoded (spec 8c): crop its margins, then turn it if it's wide. */
data class DecodeOptions(val crop: Boolean = false, val rotateWide: Boolean = false, val rotateInvert: Boolean = false)

class UnreadableImageException(cause: Throwable) : Exception("The image couldn't be decoded", cause)

/** Decodes page images with Skia (spec 2a §4): JPEG, PNG, WebP, GIF and BMP. */
object PageDecoder {
    fun decode(bytes: ByteArray, options: DecodeOptions = DecodeOptions()): DecodedPage {
        val codec = try {
            Codec.makeFromData(Data.makeFromBytes(bytes))
        } catch (e: Exception) {
            throw UnreadableImageException(e)
        }
        codec.use {
            if (it.frameCount > 1) return animated(it)
        }
        val image = try {
            Image.makeFromEncoded(bytes)
        } catch (e: Exception) {
            throw UnreadableImageException(e)
        }
        return image.use { still(it, options) }
    }

    private fun still(image: Image, options: DecodeOptions): DecodedPage.Still {
        val full = Bitmap.makeFromImage(image).let { if (options.crop) cropped(it) else it }
            .let {
                val degrees = wideRotation(it.width, it.height, options.rotateWide, options.rotateInvert)
                if (degrees != 0) turned(it, degrees) else it
            }
        val tiles = tileRanges(full.height).map { rows ->
            if (rows.count() == full.height) {
                full
            } else {
                Bitmap().also { full.extractSubset(it, IRect.makeLTRB(0, rows.first, full.width, rows.last + 1)) }
            }
        }
        return DecodedPage.Still(full.width, full.height, tiles.map { it.asComposeImageBitmap() })
    }

    private fun cropped(bitmap: Bitmap): Bitmap {
        val bounds = contentBounds(bitmap.width, bitmap.height, bitmap::getColor)
        if (bounds.left == 0 && bounds.top == 0 && bounds.right == bitmap.width && bounds.bottom == bitmap.height) {
            return bitmap
        }
        return Bitmap().also {
            bitmap.extractSubset(it, IRect.makeLTRB(bounds.left, bounds.top, bounds.right, bounds.bottom))
        }
    }

    // 90 is clockwise, −90 counterclockwise
    private fun turned(bitmap: Bitmap, degrees: Int): Bitmap {
        val result = Bitmap().apply {
            allocPixels(bitmap.imageInfo.withWidthHeight(bitmap.height, bitmap.width))
        }
        Canvas(result).use { canvas ->
            if (degrees > 0) {
                canvas.translate(bitmap.height.toFloat(), 0f)
            } else {
                canvas.translate(0f, bitmap.width.toFloat())
            }
            canvas.rotate(degrees.toFloat())
            Image.makeFromBitmap(bitmap).use { canvas.drawImage(it, 0f, 0f) }
        }
        return result
    }

    private fun animated(codec: Codec): DecodedPage.Animated {
        val frames = (0 until codec.frameCount).map { index ->
            Bitmap().also {
                it.allocPixels(codec.imageInfo)
                codec.readPixels(it, index)
            }.asComposeImageBitmap()
        }
        return DecodedPage.Animated(
            codec.width,
            codec.height,
            frames,
            codec.framesInfo.map { it.duration.coerceAtLeast(MIN_FRAME_MS) },
        )
    }

    // Browsers show 0 and 10 ms GIF frames at about 100 ms
    private const val MIN_FRAME_MS = 20
}

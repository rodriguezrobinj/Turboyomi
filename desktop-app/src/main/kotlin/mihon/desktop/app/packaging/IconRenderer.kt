package mihon.desktop.app.packaging

import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Turns the app's SVG into a Windows .ico of PNG images (spec 6a §2); the build runs it for the exe and the MSI. */
object IconRenderer {
    /** The SVG's own size, which [png] scales from. */
    private const val SVG_SIZE = 256f

    fun ico(svg: ByteArray, sizes: List<Int>): ByteArray {
        val pngs = sizes.map { png(svg, it) }
        val header = ByteBuffer.allocate(6 + 16 * sizes.size).order(ByteOrder.LITTLE_ENDIAN)
        header.putShort(0).putShort(1).putShort(sizes.size.toShort())
        var offset = header.capacity()
        sizes.zip(pngs).forEach { (size, png) ->
            // 256 is written as 0 in the one-byte width and height
            header.put((size % 256).toByte()).put((size % 256).toByte()).put(0).put(0)
            header.putShort(1).putShort(32).putInt(png.size).putInt(offset)
            offset += png.size
        }
        return ByteArrayOutputStream().apply {
            write(header.array())
            pngs.forEach(::write)
        }.toByteArray()
    }

    private fun png(svg: ByteArray, size: Int): ByteArray {
        val dom = SVGDOM(Data.makeFromBytes(svg))
        dom.setContainerSize(SVG_SIZE, SVG_SIZE)
        val surface = Surface.makeRasterN32Premul(size, size)
        surface.canvas.scale(size / SVG_SIZE, size / SVG_SIZE)
        dom.render(surface.canvas)
        return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val (svg, out) = args
        File(out).apply { parentFile.mkdirs() }.writeBytes(ico(File(svg).readBytes(), listOf(16, 24, 32, 48, 64, 256)))
    }
}

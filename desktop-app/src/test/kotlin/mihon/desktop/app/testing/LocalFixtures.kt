package mihon.desktop.app.testing

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

/** Pages and chapters for the local source's tests, in the test app's storage folder. */
object LocalFixtures {
    /** A real PNG; [size] tells pages apart. */
    fun png(size: Int = 4): ByteArray = ByteArrayOutputStream().also {
        ImageIO.write(BufferedImage(size, size, BufferedImage.TYPE_INT_RGB), "png", it)
    }.toByteArray()

    fun zip(file: File, entries: Map<String, ByteArray>): File = file.also {
        ZipOutputStream(it.outputStream()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    /** An EPUB with one page per image, in order. */
    fun epub(file: File, images: List<ByteArray>): File {
        val pages = images.indices.associate { i ->
            "OEBPS/p$i.xhtml" to """<html><body><img src="img$i.png"/></body></html>""".toByteArray()
        }
        val opf = """
            <package><manifest>${images.indices.joinToString("") { i ->
            """<item id="p$i" href="p$i.xhtml" media-type="application/xhtml+xml"/>"""
        }}</manifest><spine>${images.indices.joinToString("") { i -> """<itemref idref="p$i"/>""" }}</spine></package>
        """.trimIndent()
        return zip(
            file,
            mapOf(
                "mimetype" to "application/epub+zip".toByteArray(),
                "META-INF/container.xml" to
                    """<container><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>"""
                        .toByteArray(),
                "OEBPS/content.opf" to opf.toByteArray(),
            ) + pages + images.withIndex().associate { (i, bytes) -> "OEBPS/img$i.png" to bytes },
        )
    }

    /** The 7z sample: José/01.png, 02.png and 10.png, and a ComicInfo.xml with chapter number 7.5. */
    fun cb7(file: File): File = file.also { target ->
        javaClass.getResourceAsStream("/archives/paginas.7z")!!.use { input ->
            target.outputStream().use(input::copyTo)
        }
    }

    /** The local/ folder of the test app's storage. */
    fun localDir(graph: TestAppGraph): File = File(graph.storageManager.getLocalSourceDirectory()!!.filePath!!)
}

package mihon.core.archive

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubReaderTest {
    @TempDir
    lateinit var dir: Path

    private fun epub(entries: Map<String, String>): File = dir.resolve("libro José.epub").toFile().also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
    }

    @Test
    fun `the images of an epub come in spine order, resolved against each page's folder`() {
        val file = epub(
            mapOf(
                "mimetype" to "application/epub+zip",
                "META-INF/container.xml" to
                    """<container><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>""",
                "OEBPS/content.opf" to """
                    <package><manifest>
                    <item id="p2" href="Text/p2.xhtml" media-type="application/xhtml+xml"/>
                    <item id="p1" href="Text/p1.xhtml" media-type="application/xhtml+xml"/>
                    </manifest><spine><itemref idref="p1"/><itemref idref="p2"/></spine></package>
                """.trimIndent(),
                "OEBPS/Text/p1.xhtml" to """<html><body><img src="../Images/a.png"/></body></html>""",
                "OEBPS/Text/p2.xhtml" to
                    """<html><body><svg><image xlink:href="../Images/b.png"/></svg></body></html>""",
                "OEBPS/Images/a.png" to "A",
                "OEBPS/Images/b.png" to "B",
            ),
        )

        EpubReader(ArchiveReader(file)).use { epub ->
            val images = epub.getImagesFromPages()
            images shouldContainExactly listOf("OEBPS/Images/a.png", "OEBPS/Images/b.png")
            epub.getInputStream(images[1])!!.use { it.readBytes().decodeToString() } shouldBe "B"
        }
    }
}

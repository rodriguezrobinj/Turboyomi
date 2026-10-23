package mihon.core.archive

import com.hippo.unifile.UniFile
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ZipWriterTest {
    @TempDir
    lateinit var dir: Path

    @Test
    fun `a CBZ written from a chapter folder is read back by the archive reader`() {
        val chapter = dir.resolve("Capítulo José_tmp").toFile().apply { mkdirs() }
        chapter.resolve("001.png").writeText("uno")
        chapter.resolve("002.png").writeText("dos")
        chapter.resolve("ComicInfo.xml").writeText("<ComicInfo/>")
        val cbz = dir.resolve("Capítulo José.cbz").toFile().apply { createNewFile() }

        ZipWriter(null, UniFile.fromFile(cbz)!!).use { writer ->
            chapter.listFiles()!!.forEach { writer.write(UniFile.fromFile(it)!!) }
        }

        ArchiveReader(cbz).use { reader ->
            reader.useEntries { entries -> entries.map { it.name }.toList() } shouldContainExactlyInAnyOrder
                listOf("001.png", "002.png", "ComicInfo.xml")
            reader.getInputStream("002.png")!!.use { it.readBytes().decodeToString() } shouldBe "dos"
        }
    }
}

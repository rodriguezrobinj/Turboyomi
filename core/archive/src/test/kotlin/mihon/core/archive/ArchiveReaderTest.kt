package mihon.core.archive

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveReaderTest {
    @TempDir
    lateinit var dir: Path

    private val pngMagic = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

    private fun resource(name: String): File = File(javaClass.getResource("/archives/$name")!!.toURI())

    private fun zip(name: String, entries: Map<String, ByteArray>): File = dir.resolve(name).toFile().also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (entry, bytes) ->
                zip.putNextEntry(ZipEntry(entry))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    private fun ArchiveReader.files(): List<String> = useEntries { entries ->
        entries.filter { it.isFile }.map { it.name }.toList()
    }

    private fun ArchiveReader.bytes(name: String): ByteArray = getInputStream(name)!!.use { it.readBytes() }

    @Test
    fun `a zip lists its files with their folders, reads each one, and has no entry that isn't there`() {
        val file = zip(
            "José Pérez.cbz",
            mapOf("José/01.png" to "uno".toByteArray(), "José/02.png" to "dos".toByteArray()),
        )

        ArchiveReader(file).use { reader ->
            reader.files() shouldContainExactlyInAnyOrder listOf("José/01.png", "José/02.png")
            reader.bytes("José/02.png").decodeToString() shouldBe "dos"
            reader.getInputStream("José/99.png") shouldBe null
        }
    }

    @Test
    fun `entries stored with backslashes are named with slashes, as libarchive names them`() {
        val file = zip("windows.zip", mapOf("José\\03.png" to "tres".toByteArray()))

        ArchiveReader(file).use { reader ->
            reader.files() shouldContainExactlyInAnyOrder listOf("José/03.png")
            reader.bytes("José/03.png").decodeToString() shouldBe "tres"
        }
    }

    @Test
    fun `7z and tar samples list their pages and ComicInfo, with real images`() {
        listOf("paginas.7z", "paginas.tar").forEach { sample ->
            ArchiveReader(resource(sample)).use { reader ->
                reader.files() shouldContainExactlyInAnyOrder
                    listOf("José/01.png", "José/02.png", "José/10.png", "ComicInfo.xml")
                reader.bytes("José/10.png").take(4) shouldBe pngMagic.toList()
                reader.bytes("ComicInfo.xml").decodeToString() shouldContain "<Number>7.5</Number>"
            }
        }
    }

    @Test
    fun `a RAR4 sample lists files apart from folders and reads them`() {
        ArchiveReader(resource("rar4.rar")).use { reader ->
            reader.files() shouldContainAll listOf("test.txt", "testdir/test.txt")
            reader.files() shouldNotContain "testemptydir"
            reader.bytes("test.txt").decodeToString() shouldBe "test text document\r\n"
        }
    }

    @Test
    fun `a RAR5 sample reads its file`() {
        ArchiveReader(resource("rar5.rar")).use { reader ->
            reader.bytes("helloworld.txt").decodeToString() shouldBe "hello libarchive test suite!\n"
        }
    }

    @Test
    fun `a file that isn't an archive fails with an IOException instead of hanging`() {
        val garbage = dir.resolve("roto.cbz").toFile().apply { writeBytes(ByteArray(1024) { (it * 31).toByte() }) }

        shouldThrow<IOException> { ArchiveReader(garbage) }
    }

    @Test
    fun `pages read from several threads at once come out whole`() {
        ArchiveReader(resource("paginas.7z")).use { reader ->
            val names = listOf("José/01.png", "José/02.png", "José/10.png")
            val expected = names.associateWith { reader.bytes(it).toList() }
            val pool = Executors.newFixedThreadPool(8)
            try {
                val reads = (1..64).map { i ->
                    pool.submit(Callable { names[i % 3].let { it to reader.bytes(it).toList() } })
                }
                reads.forEach { read ->
                    val (name, bytes) = read.get()
                    bytes shouldBe expected.getValue(name)
                }
            } finally {
                pool.shutdown()
            }
        }
    }
}

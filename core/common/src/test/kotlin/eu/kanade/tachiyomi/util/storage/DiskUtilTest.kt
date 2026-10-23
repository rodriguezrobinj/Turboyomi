package eu.kanade.tachiyomi.util.storage

import com.hippo.unifile.UniFile
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class DiskUtilTest {
    @TempDir
    lateinit var dir: Path

    @Test
    fun `Windows reserved names get an underscore, with or without an extension and in any case`() {
        DiskUtil.buildValidFilename("CON") shouldBe "CON_"
        DiskUtil.buildValidFilename("com1") shouldBe "com1_"
        DiskUtil.buildValidFilename("lpt9.txt") shouldBe "lpt9_.txt"
        DiskUtil.buildValidFilename("CONSOLA") shouldBe "CONSOLA"
    }

    @Test
    fun `forbidden characters and a trailing dot or space are replaced or dropped, as in Mihon`() {
        DiskUtil.buildValidFilename("Tomo: 1?") shouldBe "Tomo_ 1_"
        DiskUtil.buildValidFilename("José. ") shouldBe "José"
    }

    @Test
    fun `the free space is what Windows reports for the folder's volume`() {
        val folder = UniFile.fromFile(dir.toFile())!!
        DiskUtil.getAvailableStorageSpace(folder) shouldBe dir.toFile().usableSpace

        val previous = DiskUtil.usableSpace
        try {
            DiskUtil.usableSpace = { 42L }
            DiskUtil.getAvailableStorageSpace(folder) shouldBe 42L
        } finally {
            DiskUtil.usableSpace = previous
        }
    }
}

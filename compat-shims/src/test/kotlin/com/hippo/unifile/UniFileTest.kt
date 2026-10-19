package com.hippo.unifile

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import kotlin.io.path.createTempDirectory

class UniFileTest {
    @Test
    fun `creates finds and lists children`() {
        val root = UniFile.fromFile(createTempDirectory("uni José").toFile())!!
        val dir = root.createDirectory("Manga A")!!
        val file = dir.createFile("cover.jpg")!!
        file.openOutputStream().use { it.write(byteArrayOf(1, 2, 3)) }

        root.findFile("Manga A")!!.isDirectory shouldBe true
        dir.listFiles()!!.map { it.name } shouldBe listOf("cover.jpg")
        file.length() shouldBe 3L
        file.openInputStream().use { it.readBytes() } shouldBe byteArrayOf(1, 2, 3)
        root.findFile("missing") shouldBe null
    }

    @Test
    fun `fromUri resolves file uris only`() {
        val dir = createTempDirectory("uni").toFile()
        UniFile.fromUri(null, android.net.Uri.fromFile(dir)) shouldNotBe null
        UniFile.fromUri(null, android.net.Uri.parse("content://x/y")) shouldBe null
    }

    @Test
    fun `delete removes directories recursively`() {
        val root = UniFile.fromFile(createTempDirectory("uni").toFile())!!
        val dir = root.createDirectory("a")!!
        dir.createFile("b")
        dir.delete() shouldBe true
        dir.exists() shouldBe false
    }
}

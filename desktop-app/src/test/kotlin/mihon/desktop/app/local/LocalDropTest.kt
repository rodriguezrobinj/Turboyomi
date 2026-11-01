package mihon.desktop.app.local

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

class LocalDropTest {
    @TempDir
    lateinit var dir: Path

    private val local get() = dir.resolve("local").createDirectories()

    @Test
    fun `a folder is a manga and an archive a chapter of the manga named after it`() {
        val folder = dir.resolve("drop/Señor de los Anillos").createDirectories()
        folder.resolve("Cap 1.cbz").writeText("1")
        folder.resolve("Cap 2/001.jpg").also { it.parent.createDirectories() }.writeText("2")
        val archive = dir.resolve("drop/One Shot.CBR").apply { writeText("3") }

        LocalDrop.import(listOf(folder, archive), local) shouldBe
            LocalDrop.Result(listOf("Señor de los Anillos", "One Shot"), emptyList())

        local.resolve("Señor de los Anillos/Cap 1.cbz").readText() shouldBe "1"
        local.resolve("Señor de los Anillos/Cap 2/001.jpg").readText() shouldBe "2"
        local.resolve("One Shot/One Shot.CBR").readText() shouldBe "3"
        // A copy: the dropped files stay
        archive.readText() shouldBe "3"
    }

    @Test
    fun `a file already in the local source is left as it is`() {
        local.resolve("Manga").createDirectories().resolve("Cap 1.cbz").writeText("mine")
        val folder = dir.resolve("drop/Manga").createDirectories()
        folder.resolve("Cap 1.cbz").writeText("dropped")
        folder.resolve("Cap 2.cbz").writeText("new")
        val archive = dir.resolve("drop/Manga.cbz").apply { writeText("dropped") }
        local.resolve("Manga/Manga.cbz").writeText("mine too")

        LocalDrop.import(listOf(folder, archive), local).mangas shouldBe listOf("Manga")

        local.resolve("Manga/Cap 1.cbz").readText() shouldBe "mine"
        local.resolve("Manga/Cap 2.cbz").readText() shouldBe "new"
        local.resolve("Manga/Manga.cbz").readText() shouldBe "mine too"
    }

    @Test
    fun `a file that isn't a chapter archive is left out`() {
        val text = dir.resolve("notes.txt").apply { writeText("x") }

        LocalDrop.import(listOf(text), local) shouldBe LocalDrop.Result(emptyList(), listOf("notes.txt"))
    }
}

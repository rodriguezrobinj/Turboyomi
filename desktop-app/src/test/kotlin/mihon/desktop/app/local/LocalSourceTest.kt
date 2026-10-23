package mihon.desktop.app.local

import eu.kanade.tachiyomi.source.model.SManga
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.testing.LocalFixtures
import mihon.desktop.app.testing.LocalFixtures.png
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.Test
import java.io.File

class LocalSourceTest {
    private val graph: TestAppGraph = startTestApp()
    private val source get() = graph.localSource

    private fun mangaDir(name: String): File = File(LocalFixtures.localDir(graph), name).apply { mkdirs() }

    private fun folderChapter(manga: File, name: String, pages: Int = 2): File =
        File(manga, name).apply { mkdirs() }.also { dir ->
            (1..pages).forEach { File(dir, "%02d.png".format(it)).writeBytes(png(it)) }
        }

    private suspend fun details(url: String): SManga =
        source.getMangaUpdate(
            SManga.create().apply {
                this.url = url
                title = url
            },
            emptyList(),
            true,
            false,
        ).manga

    @Test
    fun `lists the manga folders by name, without hidden folders or loose files, and searches by name`(): Unit =
        runBlocking {
            mangaDir("B José")
            mangaDir("a Pérez")
            mangaDir(".oculta")
            File(LocalFixtures.localDir(graph), "suelto.txt").writeText("x")

            val titles = source.getPopularManga(1).mangas.map { it.title }
            titles.filter { it in listOf("a Pérez", "B José") } shouldContainExactly listOf("a Pérez", "B José")
            titles shouldNotContain ".oculta"
            titles shouldNotContain "suelto.txt"

            source.getSearchManga(1, "pérez", source.getFilterList()).mangas.map { it.title } shouldContainExactly
                listOf("a Pérez")
        }

    @Test
    fun `details come from details json, which becomes a ComicInfo xml, or straight from ComicInfo xml`(): Unit =
        runBlocking {
            val json = mangaDir("Con JSON")
            File(json, "details.json").writeText(
                """{"title":"Título JSON","author":"Autora","genre":["Acción","Drama"],"status":1}""",
            )
            val fromJson = details("Con JSON")
            fromJson.title shouldBe "Título JSON"
            fromJson.author shouldBe "Autora"
            fromJson.genre shouldBe "Acción, Drama"
            File(json, "ComicInfo.xml").exists() shouldBe true

            val xml = mangaDir("Con XML")
            File(xml, "ComicInfo.xml").writeText(
                """<?xml version="1.0" encoding="utf-8"?>""" +
                    """<ComicInfo><Series>Serie XML</Series><Writer>Escritor</Writer>""" +
                    """<Summary>Resumen</Summary></ComicInfo>""",
            )
            val fromXml = details("Con XML")
            fromXml.title shouldBe "Serie XML"
            fromXml.author shouldBe "Escritor"
            fromXml.description shouldBe "Resumen"
        }

    @Test
    fun `cover jpg is the cover, and without one the first page of the first chapter becomes it`(): Unit =
        runBlocking {
            File(mangaDir("Con portada"), "cover.jpg").writeBytes(png(8))
            details("Con portada").thumbnail_url!! shouldEndWith "cover.jpg"

            val bare = mangaDir("Sin portada")
            folderChapter(bare, "Cap 1")
            val manga = SManga.create().apply {
                url = "Sin portada"
                title = "Sin portada"
            }
            source.getMangaUpdate(manga, emptyList(), fetchDetails = false, fetchChapters = true)
            File(bare, "cover.jpg").readBytes().toList() shouldBe png(1).toList()
        }

    @Test
    fun `chapters come from folders, CBZ, CB7 and EPUB, newest first, without the PDF, with the damaged CBZ`(): Unit =
        runBlocking {
            val dir = mangaDir("Capítulos José")
            folderChapter(dir, "Cap 1")
            LocalFixtures.zip(File(dir, "Cap 2.cbz"), mapOf("01.png" to png(1)))
            LocalFixtures.cb7(File(dir, "Cap 3.cb7"))
            LocalFixtures.epub(File(dir, "Cap 4.epub"), listOf(png(1), png(2)))
            File(dir, "Cap 5.pdf").writeText("no")
            File(dir, "Cap 6.cbz").writeBytes(ByteArray(512) { (it * 7).toByte() })

            val manga = SManga.create().apply {
                url = "Capítulos José"
                title = "Capítulos José"
            }
            val chapters = source.getMangaUpdate(manga, emptyList(), fetchDetails = false, fetchChapters = true)
                .chapters

            chapters.map { it.name } shouldContainExactly listOf("Cap 6", "Cap 4", "Cap 3", "Cap 2", "Cap 1")
            chapters.single { it.name == "Cap 3" }.chapter_number shouldBe 7.5f // from its ComicInfo.xml, not its name
            chapters.single { it.name == "Cap 2" }.url shouldBe "Capítulos José/Cap 2.cbz"
        }
}

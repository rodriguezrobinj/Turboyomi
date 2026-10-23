package mihon.desktop.app.download

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.Test
import java.io.File

class DownloadProviderTest {
    private val graph: TestAppGraph = startTestApp()
    private val provider get() = graph.downloadProvider
    private val source = TestSources.http

    private fun downloads(): File = File(graph.storageManager.getDownloadsDirectory()!!.filePath!!)

    @Test
    fun `reserved and forbidden names are sanitized, and the chapter is found again`(): Unit = runBlocking {
        provider.getMangaDirName("CON") shouldBe "CON_"
        provider.getChapterDirName("Tomo: 1?", null, "/c1") shouldStartWith "Tomo_ 1_"

        val mangaDir = provider.getMangaDir("CON", source).getOrThrow()
        mangaDir.createDirectory(provider.getChapterDirName("Tomo: 1?", null, "/c1"))

        provider.findChapterDir("Tomo: 1?", null, "/c1", "CON", source).shouldNotBeNull()
    }

    @Test
    fun `a path longer than 260 characters is written and found`(): Unit = runBlocking {
        val title = "José " + "x".repeat(150)
        val chapter = "Capítulo " + "y".repeat(150)
        val dir = provider.getMangaDir(title, source).getOrThrow().createDirectory(
            provider.getChapterDirName(chapter, null, "/largo"),
        )!!
        File(dir.filePath!!, "001.png").writeText("p")
        (File(dir.filePath!!).absolutePath.length > 260) shouldBe true

        provider.findChapterDir(chapter, null, "/largo", title, source).shouldNotBeNull()
    }

    @Test
    fun `a downloads folder copied from Android, with Mihon's layout, is recognized`(): Unit = runBlocking {
        val chapterDir = File(
            downloads(),
            "${provider.getSourceDirName(source)}/Copiado de Android/" +
                provider.getChapterDirName("Capítulo 1", null, "/android/c1"),
        ).apply { mkdirs() }
        File(chapterDir, "001.png").writeText("p")

        provider.findChapterDir("Capítulo 1", null, "/android/c1", "Copiado de Android", source).shouldNotBeNull()
    }
}

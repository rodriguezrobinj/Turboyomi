package mihon.desktop.app.download

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.time.Duration.Companion.seconds

class DownloadCacheTest {
    private val graph: TestAppGraph = startTestApp()

    private fun isDownloaded(chapter: String, manga: String) = graph.downloadCache.isChapterDownloaded(
        chapter,
        null,
        "/cache/$chapter",
        manga,
        TestSources.http.id,
    )

    @Test
    fun `chapters on disk are seen as downloaded, and stop being so when deleted outside the app`(): Unit =
        runBlocking {
            val provider = graph.downloadProvider
            val mangaDir = File(provider.getMangaDir("Caché José", TestSources.http).getOrThrow().filePath!!)
            val chapterDir = File(mangaDir, provider.getChapterDirName("Capítulo 1", null, "/cache/Capítulo 1"))
            chapterDir.mkdirs()
            File(chapterDir, "001.png").writeText("p")

            graph.downloadCache.invalidateCache()
            withTimeout(10.seconds) { while (!isDownloaded("Capítulo 1", "Caché José")) delay(100) }

            chapterDir.deleteRecursively()
            graph.downloadCache.invalidateCache()
            withTimeout(10.seconds) { while (isDownloaded("Capítulo 1", "Caché José")) delay(100) }
            isDownloaded("Capítulo 1", "Caché José") shouldBe false
        }
}

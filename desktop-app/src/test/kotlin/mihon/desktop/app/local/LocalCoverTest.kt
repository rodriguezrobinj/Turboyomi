package mihon.desktop.app.local

import eu.kanade.tachiyomi.util.editCover
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.testing.LocalFixtures
import mihon.desktop.app.testing.LocalFixtures.png
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.source.local.LocalSource
import java.io.File

class LocalCoverTest {
    private val graph = startTestApp()

    @Test
    fun `editing a local manga's cover writes cover jpg in its folder, not in the cover cache`(): Unit = runBlocking {
        val folder = File(LocalFixtures.localDir(graph), "Portada José").apply { mkdirs() }
        val manga = graph.networkToLocalManga(
            Manga.create().copy(url = "Portada José", title = "Portada José", source = LocalSource.ID),
        )

        manga.editCover(graph.localCoverManager, png(9).inputStream(), graph.updateManga, graph.coverCache)

        File(folder, "cover.jpg").readBytes().toList() shouldBe png(9).toList()
        graph.coverCache.getCustomCoverFile(manga.id).exists() shouldBe false
    }
}

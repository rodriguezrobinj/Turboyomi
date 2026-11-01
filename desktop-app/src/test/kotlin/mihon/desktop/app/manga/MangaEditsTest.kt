package mihon.desktop.app.manga

import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.MangaRemoteUpdate

class MangaEditsTest {
    private val edit = MangaEdits.Edit(
        title = "Señor de las Moscas",
        author = "José",
        artist = "",
        description = "Mía",
        genre = listOf("Acción", "Drama"),
        status = SManga.COMPLETED.toLong(),
    )

    private fun fromSource(title: String?) = MangaRemoteUpdate(
        id = 1, title = title, author = "Fuente", artist = "Fuente", description = "De la fuente",
        genre = listOf("Otro"), status = SManga.ONGOING.toLong(), thumbnailUrl = "https://cover",
        updateStrategy = UpdateStrategy.ALWAYS_UPDATE, memo = JsonObject(emptyMap()), initialized = true,
        coverLastModified = null,
    )

    @Test
    fun `what the user edited wins over a refresh, which leaves the edited title alone`() {
        fromSource("Lord of the Flies").withEdit(edit) shouldBe fromSource(null).copy(
            author = "José",
            artist = "",
            description = "Mía",
            genre = listOf("Acción", "Drama"),
            status = SManga.COMPLETED.toLong(),
        )
        fromSource("Lord of the Flies").withEdit(null) shouldBe fromSource("Lord of the Flies")
    }

    @Test
    fun `a saved edit is in the manga's details and in the file, until it's reset`(): Unit = runBlocking {
        val graph = startTestApp()
        val manga = TestLibrary.libraryManga(graph, TestLibrary.category(graph))

        graph.mangaEdits.save(manga, edit)

        val saved = graph.getManga.await(manga.id)!!
        saved.title shouldBe edit.title
        saved.author shouldBe "José"
        saved.genre shouldBe listOf("Acción", "Drama")
        saved.status shouldBe SManga.COMPLETED.toLong()
        // Read back from manga-edits.json, as on the next start, with the title reset puts back
        MangaEdits(graph.appDirs, graph.mangaRepository, graph.downloadManager).get(saved) shouldBe
            edit.copy(sourceTitle = manga.title)

        graph.mangaEdits.clear(saved)
        MangaEdits(graph.appDirs, graph.mangaRepository, graph.downloadManager).get(saved) shouldBe null
        graph.getManga.await(manga.id)!!.title shouldBe manga.title
    }
}

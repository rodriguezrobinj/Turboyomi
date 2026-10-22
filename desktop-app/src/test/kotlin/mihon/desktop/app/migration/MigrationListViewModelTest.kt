package mihon.desktop.app.migration

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.app.testing.FakeSource
import mihon.desktop.app.testing.FakeSourceManager
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import mihon.feature.migration.list.MigrationListViewModel
import mihon.feature.migration.list.models.MigratingManga.SearchResult
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

class MigrationListViewModelTest {
    private val graph: TestAppGraph = startTestApp().also { graph ->
        (graph.sourceManager as FakeSourceManager).sources.update { sources ->
            sources + listOf(failing, destination, other).filter { it !in sources }
        }
    }

    /** A library manga of [TestSources.updatable] (or [sourceId]) with [title] and a url of its own. */
    private suspend fun libraryManga(title: String, sourceId: Long = TestSources.updatable.id, url: String? = null) =
        graph.networkToLocalManga(
            Manga.create().copy(url = url ?: "/lista/${next.incrementAndGet()}", title = title, source = sourceId),
        ).also { graph.updateManga.awaitUpdateFavorite(it.id, true) }

    private suspend fun searched(vararg mangas: Manga, sources: List<FakeSource>): MigrationListViewModel {
        graph.sourcePreferences.migrationSources.set(sources.map { it.id })
        val viewModel = graph.migrationListViewModelFactory.create(mangas.map { it.id }, null)
        withTimeout(20.seconds) {
            viewModel.state.first { it.items.size == mangas.size && it.finishedCount == mangas.size }
        }
        return viewModel
    }

    private fun MigrationListViewModel.result(manga: Manga) =
        state.value.items.single { it.manga.id == manga.id }.searchResult.value

    @Test
    fun `a source that fails is skipped and the next one finds the manga`(): Unit = runBlocking {
        val manga = libraryManga("Manga 7")
        failing.failNext = IOException("sin red")

        val viewModel = searched(manga, sources = listOf(failing, destination))

        val found = viewModel.result(manga).shouldBeInstanceOf<SearchResult.Success>()
        found.manga.source shouldBe destination.id
        found.manga.title shouldBe "Manga 7"
        found.chapterCount shouldBe 5
    }

    @Test
    fun `nothing similar in any source leaves it not found`(): Unit = runBlocking {
        val manga = libraryManga("Zzz sin coincidencias")

        val viewModel = searched(manga, sources = listOf(destination))

        viewModel.result(manga) shouldBe SearchResult.NotFound
    }

    @Test
    fun `the origin's own source never returns the same manga as its match`(): Unit = runBlocking {
        val manga = libraryManga("Manga 9", sourceId = destination.id, url = "/manga/9")

        val viewModel = searched(manga, sources = listOf(destination, other))

        viewModel.result(manga).shouldBeInstanceOf<SearchResult.Success>().manga.source shouldBe other.id
    }

    @Test
    fun `migrate all skips the not found ones`(): Unit = runBlocking {
        val found = libraryManga("Manga 8")
        val missing = libraryManga("Zzz tampoco existe")
        val viewModel = searched(found, missing, sources = listOf(destination))
        val target = viewModel.result(found).shouldBeInstanceOf<SearchResult.Success>().manga

        val back = async { viewModel.navigateBackEvent.first() }
        viewModel.migrateMangas()
        withTimeout(20.seconds) { back.await() }

        graph.getManga.await(found.id)!!.favorite shouldBe false
        graph.getManga.await(target.id)!!.favorite shouldBe true
        graph.getManga.await(missing.id)!!.favorite shouldBe true
    }

    @Test
    fun `migrate all with no match at all migrates nothing and goes back`(): Unit = runBlocking {
        val missing = libraryManga("Zzz ninguno")
        val viewModel = searched(missing, sources = listOf(destination))

        val back = async { viewModel.navigateBackEvent.first() }
        viewModel.migrateMangas()
        withTimeout(20.seconds) { back.await() }

        graph.getManga.await(missing.id)!!.favorite shouldBe true
    }

    private companion object {
        val next = AtomicInteger()
        val failing = FakeSource(id = 21L, name = "Falla")
        val destination = FakeSource(id = 22L, name = "Destino")
        val other = FakeSource(id = 23L, name = "Otra")
    }
}

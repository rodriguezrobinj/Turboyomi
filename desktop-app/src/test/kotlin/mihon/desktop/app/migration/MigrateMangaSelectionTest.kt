package mihon.desktop.app.migration

import eu.kanade.tachiyomi.ui.browse.migration.manga.MigrateMangaViewModel
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import kotlin.time.Duration.Companion.seconds

/** The Migrate tab's manga list selects like the library (spec 3a §3 A): shift+click for a range, Ctrl+A for all. */
class MigrateMangaSelectionTest {
    private val graph: TestAppGraph = startTestApp()

    /** Four library manga of a source of their own, listed by title: A, B, C, D. */
    private suspend fun titles(sourceId: Long): Pair<MigrateMangaViewModel, List<Manga>> {
        val mangas = listOf("D", "B", "A", "C").map { title ->
            graph.networkToLocalManga(
                Manga.create().copy(url = "/sel/$sourceId/$title", title = title, source = sourceId),
            )
                .also { graph.updateManga.awaitUpdateFavorite(it.id, true) }
        }
        val viewModel = graph.migrateMangaViewModelFactory.create(sourceId)
        withTimeout(10.seconds) { viewModel.state.first { !it.isLoading && it.titles.size == 4 } }
        return viewModel to mangas.sortedBy { it.title }
    }

    /** The selection once the state has caught up with [expected] (it's combined off the main thread). */
    private suspend fun MigrateMangaViewModel.awaitSelection(expected: Set<Long>) {
        withTimeoutOrNull(5.seconds) { state.first { it.selection == expected } }
        state.value.selection shouldBe expected
    }

    @Test
    fun `shift+click selects every title from the last one clicked`(): Unit = runBlocking {
        val (viewModel, titles) = titles(sourceId = 41L)
        val (_, b, c, d) = titles

        viewModel.toggleSelection(d)
        viewModel.selectRange(b)

        viewModel.awaitSelection(setOf(b.id, c.id, d.id))
    }

    @Test
    fun `shift+click with nothing clicked before just selects that title`(): Unit = runBlocking {
        val (viewModel, titles) = titles(sourceId = 42L)

        viewModel.selectRange(titles[2])

        viewModel.awaitSelection(setOf(titles[2].id))
    }

    @Test
    fun `Ctrl+A selects every title`(): Unit = runBlocking {
        val (viewModel, titles) = titles(sourceId = 43L)

        viewModel.selectAll()

        viewModel.awaitSelection(titles.map { it.id }.toSet())
    }
}

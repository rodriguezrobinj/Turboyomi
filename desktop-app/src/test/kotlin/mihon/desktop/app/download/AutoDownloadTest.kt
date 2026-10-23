package mihon.desktop.app.download

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.runComposeUiTest
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.more.settings.screen.SettingsDownloadScreen
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.app.main.AppProviders
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalTestApi::class)
class AutoDownloadTest {
    @Test
    fun `with download new chapters on, a library update downloads the new chapters`(): Unit = runBlocking {
        val graph = startTestApp()
        graph.downloadPreferences.downloadNewChapters.set(true)
        graph.libraryPreferences.autoUpdateMangaRestrictions.set(emptySet())
        TestSources.http.pageCount = 2
        val category = TestLibrary.category(graph)
        // A library manga whose three chapters aren't in the database yet: all three are new to the update
        val manga = graph.networkToLocalManga(
            Manga.create().copy(url = "/auto/1", title = "Automática José", source = TestSources.http.id),
        )
        graph.updateManga.awaitUpdateFavorite(manga.id, true)
        graph.setMangaCategories.await(manga.id, listOf(category))

        graph.libraryUpdateJob.start(category) shouldBe true
        withTimeout(30.seconds) { graph.libraryUpdateJob.awaitIdle() }

        val chapters = graph.getChaptersByMangaId.await(manga.id)
        chapters.size shouldBe 3
        withTimeout(60.seconds) {
            while (
                !chapters.all {
                    graph.downloadManager.isChapterDownloaded(it.name, it.scanlator, it.url, manga.title, manga.source)
                }
            ) {
                delay(200)
            }
        }
        graph.downloadPreferences.downloadNewChapters.set(false)
    }

    @Test
    fun `Settings › Downloads has Mihon's options, without Wi-Fi or tall image splitting`() = runComposeUiTest {
        val graph = startTestApp()
        setContent {
            AppProviders(graph) { Navigator(SettingsDownloadScreen) { DefaultNavigatorScreenTransition(it) } }
        }

        waitUntil(timeoutMillis = 15_000) {
            onAllNodes(hasText(str(MR.strings.save_chapter_as_cbz))).fetchSemanticsNodes().isNotEmpty()
        }
        onAllNodes(hasText(str(MR.strings.connected_to_wifi))).fetchSemanticsNodes().size shouldBe 0
        onAllNodes(hasText(str(MR.strings.split_tall_images))).fetchSemanticsNodes().size shouldBe 0
    }
}

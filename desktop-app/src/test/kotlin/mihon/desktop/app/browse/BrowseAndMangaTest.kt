package mihon.desktop.app.browse

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.runComposeUiTest
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.main.AppProviders
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR
import java.io.IOException

@OptIn(ExperimentalTestApi::class)
class BrowseAndMangaTest {
    private fun androidx.compose.ui.test.ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 15_000) { onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `browsing a source, opening a manga saves it with its chapters, and a chapter is marked read`() =
        runComposeUiTest {
            val graph = startTestApp()
            setContent {
                AppProviders(graph) {
                    Navigator(BrowseSourceScreen(TestSources.fake.id, null)) { DefaultNavigatorScreenTransition(it) }
                }
            }

            waitForText("Manga 1")
            onNodeWithText("Manga 1").performClick()
            waitForText("Chapter 5")
            waitForText("Description of Manga 1")
            // Downloads arrive in subproject 4b: no chapter shows a download button
            onAllNodesWithContentDescription(str(MR.strings.manga_download)).fetchSemanticsNodes().size shouldBe 0

            val manga = runBlocking { graph.getMangaByUrlAndSourceId.await("/manga/1", TestSources.fake.id) }!!
            runBlocking { graph.getChaptersByMangaId.await(manga.id) }.size shouldBe 5

            onNodeWithText("Chapter 3").performMouseInput { rightClick() }
            onNodeWithText(str(MR.strings.action_mark_as_read)).performClick()
            waitUntil(timeoutMillis = 10_000) {
                runBlocking { graph.getChaptersByMangaId.await(manga.id) }.single { it.name == "Chapter 3" }.read
            }
        }

    @Test
    fun `a source that fails to load shows the error with retry instead of crashing`() = runComposeUiTest {
        val graph = startTestApp()
        TestSources.fake.failNext = IOException("Sin conexión con José Pérez")
        setContent {
            AppProviders(graph) {
                Navigator(BrowseSourceScreen(TestSources.fake.id, null)) { DefaultNavigatorScreenTransition(it) }
            }
        }

        waitForText("Sin conexión con José Pérez")
        onNodeWithText(str(MR.strings.action_retry)).performClick()
        waitForText("Manga 1")
    }
}

package eu.kanade.presentation.more.settings.screen.advanced

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.main.AppProviders
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR

@OptIn(ExperimentalTestApi::class)
class ClearDatabaseScreenTest {
    // An uninstalled source of its own: other tests share the database and keep their manga
    private val sourceId = 911_000_111L

    @Test
    fun `deleting a source's manga keeps the library`() = runComposeUiTest {
        val graph = startTestApp()
        val (stray, kept) = runBlocking {
            val stray = graph.networkToLocalManga(
                Manga.create().copy(url = "/stray", title = "Stray", source = sourceId),
            )
            stray to TestLibrary.libraryManga(graph, TestLibrary.category(graph), sourceId)
        }
        setContent {
            AppProviders(graph) { Navigator(ClearDatabaseScreen()) { DefaultNavigatorScreenTransition(it) } }
        }

        val row = hasText(sourceId.toString(), substring = true) and hasClickAction()
        waitUntil(timeoutMillis = 30_000) { onAllNodes(row).fetchSemanticsNodes().isNotEmpty() }
        val delete = onNode(hasText(str(MR.strings.action_delete)) and hasClickAction())
        delete.assertIsNotEnabled()

        onNode(row).performClick()
        delete.assertIsEnabled().performClick()
        onNodeWithText(str(MR.strings.action_ok)).performClick()

        waitUntil(timeoutMillis = 30_000) { runBlocking { graph.getManga.await(stray.id) } == null }
        runBlocking {
            graph.getManga.await(stray.id).shouldBeNull()
            graph.getManga.await(kept.id).shouldNotBeNull()
        }
    }
}

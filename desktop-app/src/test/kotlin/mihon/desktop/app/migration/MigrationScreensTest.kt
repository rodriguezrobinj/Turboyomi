package mihon.desktop.app.migration

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.main.AppContent
import mihon.desktop.app.main.AppEvents
import mihon.desktop.app.main.AppProviders
import mihon.desktop.app.main.Shortcut
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR

@OptIn(ExperimentalTestApi::class)
class MigrationScreensTest {
    // Unmerged: the extended FAB of the config screen doesn't merge its label into the button
    private fun androidx.compose.ui.test.ComposeUiTest.waitForText(text: String) = waitUntil(timeoutMillis = 15_000) {
        onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun `Migrate in a library manga's menu opens the config, and Continue opens the manual search`() =
        runComposeUiTest {
            val graph = startTestApp()
            val manga = runBlocking { TestLibrary.libraryManga(graph, TestLibrary.category(graph)) }
            setContent {
                AppProviders(graph) {
                    Navigator(MangaScreen(manga.id)) { DefaultNavigatorScreenTransition(it) }
                }
            }

            waitForText(manga.title)
            onNodeWithContentDescription(str(MR.strings.action_menu_overflow_description)).performClick()
            onNodeWithText(str(MR.strings.action_migrate)).performClick()
            waitForText(str(MR.strings.migrationConfigScreen_continueButtonText))

            onNodeWithText(str(MR.strings.migrationConfigScreen_continueButtonText), useUnmergedTree = true)
                .performClick()
            // One manga: straight to the global search over the chosen sources, with its title as the query
            waitForText(manga.title)
        }

    @Test
    fun `Migrate on a library selection opens the config`() = runComposeUiTest {
        val graph = startTestApp()
        val category = runBlocking { TestLibrary.category(graph) }
        val name = runBlocking { graph.getCategories.await().first { it.id == category }.name }
        val manga = runBlocking { TestLibrary.libraryManga(graph, category) }
        setContent { AppContent(graph, crash = null, onRestart = {}) }

        waitForText(name)
        onNodeWithText(name).performClick()
        waitForText(manga.title)
        runOnIdle { AppEvents.send(Shortcut.SelectAll) }
        // With downloads the bottom bar overflows: Migrate is in its More menu
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(hasContentDescription(str(MR.strings.label_more))).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithContentDescription(str(MR.strings.label_more)).performClick()
        waitForText(str(MR.strings.migrate))
        onAllNodes(hasText(str(MR.strings.migrate)), useUnmergedTree = true).onFirst().performClick()
        waitForText(str(MR.strings.migrationConfigScreen_continueButtonText))
    }
}

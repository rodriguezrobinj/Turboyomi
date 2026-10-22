package mihon.desktop.app.library

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import io.kotest.matchers.collections.shouldContain
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.main.AppProviders
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR

@OptIn(ExperimentalTestApi::class)
class CategoriesTest {
    @Test
    fun `a category created on the categories screen is saved`() = runComposeUiTest {
        val graph = startTestApp()
        setContent { AppProviders(graph) { Navigator(CategoryScreen()) } }

        // The extended FAB hides its label from the merged tree
        onNodeWithText(str(MR.strings.action_add), useUnmergedTree = true).performClick()
        onNode(hasSetTextAction()).performTextInput("Leyendo ñandú")
        // The dialog's confirm button, over the screen's add button
        onAllNodesWithText(str(MR.strings.action_add)).onLast().performClick()

        waitUntil(timeoutMillis = 10_000) {
            runBlocking { graph.getCategories.await() }.any { it.name == "Leyendo ñandú" }
        }
    }

    @Test
    fun `adding a manga to the library with categories asks which ones and saves the choice`() = runComposeUiTest {
        val graph = startTestApp()
        val category = runBlocking { TestLibrary.category(graph) }
        val name = runBlocking { graph.getCategories.await().first { it.id == category }.name }
        val (manga, _) = runBlocking { TestLibrary.httpManga(graph) }
        setContent { AppProviders(graph) { Navigator(MangaScreen(manga.id)) } }

        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(hasText(str(MR.strings.add_to_library))).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText(str(MR.strings.add_to_library)).performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText(name)).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText(name).performClick()
        onNodeWithText(str(MR.strings.action_ok)).performClick()

        waitUntil(timeoutMillis = 10_000) { runBlocking { graph.getManga.await(manga.id)!!.favorite } }
        runBlocking { graph.getCategories.await(manga.id) }.map { it.id } shouldContain category
    }
}

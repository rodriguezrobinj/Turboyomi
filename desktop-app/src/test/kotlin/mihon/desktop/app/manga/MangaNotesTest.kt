package mihon.desktop.app.manga

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import eu.kanade.tachiyomi.ui.manga.notes.MangaNotesScreen
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.main.AppProviders
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class MangaNotesTest {
    @Test
    fun `a note typed on the notes screen is saved to the manga`() = runComposeUiTest {
        val graph = startTestApp()
        val manga = runBlocking { TestLibrary.libraryManga(graph, TestLibrary.category(graph)) }
        setContent {
            AppProviders(graph) { Navigator(MangaNotesScreen(manga)) { DefaultNavigatorScreenTransition(it) } }
        }

        waitUntil(timeoutMillis = 15_000) { onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        onNode(hasSetTextAction()).performTextInput("Nota de José")

        waitUntil(timeoutMillis = 10_000) {
            runBlocking { graph.getManga.await(manga.id)!!.notes }.contains("Nota de José")
        }
        runBlocking { graph.getManga.await(manga.id)!!.notes } shouldContain "Nota de José"
    }
}

package mihon.desktop.app.library

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.main.AppContent
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR

@OptIn(ExperimentalTestApi::class)
class LibraryTabTest {
    @Test
    fun `the app opens on the library, showing a manga in its category`() = runComposeUiTest {
        val graph = startTestApp()
        val category = runBlocking { TestLibrary.category(graph) }
        val name = runBlocking { graph.getCategories.await().first { it.id == category }.name }
        val manga = runBlocking { TestLibrary.libraryManga(graph, category) }
        setContent { AppContent(graph, crash = null, onRestart = {}) }

        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText(name)).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText(name).performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText(manga.title)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `a running update shows its progress with Cancel, which stops it`() = runComposeUiTest {
        val graph = startTestApp()
        val category = runBlocking { TestLibrary.category(graph) }
        runBlocking { TestLibrary.libraryManga(graph, category) }
        // Mihon's default restrictions skip a manga with unread chapters, and this one has three
        graph.libraryPreferences.autoUpdateMangaRestrictions.set(emptySet())
        val gate = CompletableDeferred<Unit>().also { TestSources.updatable.gate = it }
        setContent { AppContent(graph, crash = null, onRestart = {}) }

        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(hasText(str(MR.strings.label_library))).fetchSemanticsNodes().isNotEmpty()
        }
        graph.libraryUpdateJob.start(category)
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(hasText(str(MR.strings.action_cancel))).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithText(str(MR.strings.action_cancel)).performClick()
        gate.complete(Unit)
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(hasText(str(MR.strings.action_cancel))).fetchSemanticsNodes().isEmpty()
        }
        TestSources.updatable.gate = null
    }
}

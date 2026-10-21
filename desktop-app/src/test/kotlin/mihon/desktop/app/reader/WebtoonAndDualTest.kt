package mihon.desktop.app.reader

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.window.WindowState
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.main.AppProviders
import mihon.desktop.app.main.ConnectWindowBack
import mihon.desktop.app.platform.LocalWindowControls
import mihon.desktop.app.platform.WindowControls
import mihon.desktop.app.reader.viewer.WEBTOON_TAG
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter

@OptIn(ExperimentalTestApi::class)
class WebtoonAndDualTest {
    private object Below : Screen() {
        @Composable
        override fun Content() {
            Text("pantalla anterior")
        }
    }

    /** Opens the first of three chapters; returns them. */
    private fun ComposeUiTest.open(mode: ReadingMode, dual: ReaderPreferences.DualPageView): List<Chapter> {
        val graph = startTestApp()
        TestSources.http.pageCount = 6
        val (manga, chapters) = runBlocking { TestLibrary.httpManga(graph) }
        graph.readerPreferences.defaultReadingMode.set(mode.flagValue)
        graph.readerPreferences.dualPageView.set(dual)
        setContent {
            AppProviders(graph) {
                CompositionLocalProvider(LocalWindowControls provides WindowControls(WindowState())) {
                    ConnectWindowBack()
                    Navigator(listOf(Below, ReaderScreen(manga.id, chapters[0].id))) {
                        DefaultNavigatorScreenTransition(it)
                    }
                }
            }
        }
        return chapters
    }

    // A page is tagged "page N" while it loads and "page N ready" once drawn: "page N" waits for either
    private fun ComposeUiTest.waitForTag(tag: String) = waitUntil(timeoutMillis = 15_000) {
        val matcher = if (tag.endsWith(" ready")) hasTestTag(tag) else hasTestTag(tag) or hasTestTag("$tag ready")
        onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun `webtoon shows the pages one under another and the end key reaches the last one`() = runComposeUiTest {
        val chapters = open(ReadingMode.WEBTOON, ReaderPreferences.DualPageView.NEVER)
        waitForTag("page 1 ready")
        onNodeWithTag(WEBTOON_TAG).assertExists()
        onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.MoveEnd) }
        waitForTag("page 6 ready")
        waitForIdle()

        // The last page in view, with the way to the next chapter under it, is still this chapter
        fun leftChapter() = runBlocking { startTestApp().getHistory.await(chapters[0].mangaId) }
            .any { it.chapterId == chapters[0].id }
        onNodeWithTag("page 6 ready").assertExists()
        leftChapter() shouldBe false

        // Going on past the end loads the next one (spec 2a §4), which writes this chapter's history
        repeat(6) {
            onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.PageDown) }
            waitForIdle()
        }
        waitUntil(timeoutMillis = 10_000) { leftChapter() }
    }

    @Test
    fun `dual page shows two portrait pages side by side`() = runComposeUiTest {
        open(ReadingMode.LEFT_TO_RIGHT, ReaderPreferences.DualPageView.ALWAYS)
        waitForTag("page 1 ready")
        waitForTag("page 2 ready")
        onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.DirectionRight) }
        waitForTag("page 3")
    }
}

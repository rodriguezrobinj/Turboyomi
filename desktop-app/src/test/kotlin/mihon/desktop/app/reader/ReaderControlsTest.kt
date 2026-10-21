package mihon.desktop.app.reader

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.window.WindowState
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.main.AppProviders
import mihon.desktop.app.main.ConnectWindowBack
import mihon.desktop.app.platform.LocalWindowControls
import mihon.desktop.app.platform.WindowControls
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR
import java.io.IOException

@OptIn(ExperimentalTestApi::class)
class ReaderControlsTest {
    private object Below : Screen() {
        @Composable
        override fun Content() {
            Text("pantalla anterior")
        }
    }

    private fun ComposeUiTest.open(failPageList: Boolean = false) {
        val graph = startTestApp()
        TestSources.http.pageCount = 5
        val (manga, chapters) = runBlocking { TestLibrary.httpManga(graph) }
        graph.readerPreferences.defaultReadingMode.set(ReadingMode.LEFT_TO_RIGHT.flagValue)
        if (failPageList) TestSources.http.failPageList = IOException("Sin páginas de José")
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
    }

    private fun ComposeUiTest.waitFor(text: String) =
        waitUntil(timeoutMillis = 15_000) {
            onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

    private fun ComposeUiTest.waitForTag(tag: String) =
        waitUntil(timeoutMillis = 15_000) { onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `a click in the middle shows the controls with the manga and chapter`() = runComposeUiTest {
        open()
        waitForTag("page 1 ready")
        onNodeWithTag(READER_TAG).performMouseInput { click(center) }
        waitFor("Capítulo 1")
        onNodeWithText("Manga HTTP", substring = true).assertExists()
    }

    @Test
    fun `switching the reading mode keeps the page being read`() = runComposeUiTest {
        open()
        waitForTag("page 1 ready")
        onNodeWithTag(READER_TAG).performKeyInput {
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionRight)
        }
        waitForTag("page 3 ready")

        onNodeWithTag(READER_TAG).performMouseInput { click(center) }
        // A single click is told apart from a double click (zoom) only after the double-click timeout
        waitForTag(QUICK_SETTINGS_TAG)
        onNodeWithTag(QUICK_SETTINGS_TAG).performClick()
        onNodeWithText(str(MR.strings.vertical_viewer)).performClick()

        // Page 3 again, now in the vertical pager (the old horizontal one had it too)
        val inVerticalPager = hasAnyAncestor(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        waitUntil(timeoutMillis = 15_000) {
            onAllNodes(hasTestTag("page 3 ready") and inVerticalPager).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `a chapter whose page list fails offers retry and opening it in the browser`() = runComposeUiTest {
        open(failPageList = true)
        waitFor("José")
        onNodeWithText(str(MR.strings.action_open_in_browser)).assertExists()
        onNodeWithText(str(MR.strings.action_retry)).performClick()
        waitForTag("page 1 ready")
    }
}

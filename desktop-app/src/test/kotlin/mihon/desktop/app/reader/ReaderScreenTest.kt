package mihon.desktop.app.reader

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.center
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
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
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.main.AppEvents
import mihon.desktop.app.main.AppProviders
import mihon.desktop.app.main.ConnectWindowBack
import mihon.desktop.app.main.Shortcut
import mihon.desktop.app.platform.LocalWindowControls
import mihon.desktop.app.platform.WindowControls
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR

@OptIn(ExperimentalTestApi::class)
class ReaderScreenTest {
    private object Below : Screen() {
        @Composable
        override fun Content() {
            Text("pantalla anterior")
        }
    }

    // A page is tagged "page N" while it loads and "page N ready" once drawn: "page N" waits for either
    private fun ComposeUiTest.waitForTag(tag: String) = waitUntil(timeoutMillis = 15_000) {
        val matcher = if (tag.endsWith(" ready")) hasTestTag(tag) else hasTestTag(tag) or hasTestTag("$tag ready")
        onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
    }

    private fun ComposeUiTest.openReader(controls: WindowControls, corruptFirstPage: Boolean = false) {
        val graph = startTestApp()
        TestSources.http.pageCount = 5
        val (manga, chapters) = runBlocking { TestLibrary.httpManga(graph) }
        if (corruptFirstPage) TestSources.images.corrupt += "${chapters[0].url}/0.png"
        // Left to right, so → is "next page"
        graph.readerPreferences.defaultReadingMode.set(ReadingMode.LEFT_TO_RIGHT.flagValue)
        setContent {
            AppProviders(graph) {
                CompositionLocalProvider(LocalWindowControls provides controls) {
                    ConnectWindowBack()
                    Navigator(Below) { navigator ->
                        // Pushed like the manga screen does: a stack built with the reader already on it would
                        // register the navigator's own back handler after the reader's, and it would win
                        LaunchedEffect(navigator) { navigator.push(ReaderScreen(manga.id, chapters[0].id)) }
                        DefaultNavigatorScreenTransition(navigator)
                    }
                }
            }
        }
        waitForTag("page 1")
    }

    @Test
    fun `the right arrow turns to the next page`() = runComposeUiTest {
        openReader(WindowControls(WindowState()))
        onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.DirectionRight) }
        waitForTag("page 2")
    }

    @Test
    fun `F11 toggles fullscreen, Esc leaves it first and then closes the reader`() = runComposeUiTest {
        val controls = WindowControls(WindowState())
        openReader(controls)

        onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.F11) }
        waitForIdle()
        controls.isFullscreen shouldBe true

        // Esc reaches the reader as Compose's back event, which ConnectWindowBack forwards
        runOnIdle { AppEvents.send(Shortcut.Back) }
        waitForIdle()
        controls.isFullscreen shouldBe false
        onNodeWithTag(READER_TAG).assertExists()

        runOnIdle { AppEvents.send(Shortcut.Back) }
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(hasText("pantalla anterior")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `the fullscreen button in the bottom bar turns fullscreen on and off`() = runComposeUiTest {
        val controls = WindowControls(WindowState())
        openReader(controls)
        waitForTag("page 1 ready")
        onNodeWithTag(READER_TAG).performMouseInput { click(center) }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(FULLSCREEN_TAG).fetchSemanticsNodes().isNotEmpty() }

        onNodeWithTag(FULLSCREEN_TAG).performClick()
        waitForIdle()
        controls.isFullscreen shouldBe true

        onNodeWithTag(FULLSCREEN_TAG).performClick()
        waitForIdle()
        controls.isFullscreen shouldBe false
    }

    @Test
    fun `a page that can't be decoded shows the error with retry and the next one still reads`() = runComposeUiTest {
        openReader(WindowControls(WindowState()), corruptFirstPage = true)
        waitUntil(timeoutMillis = 15_000) {
            onAllNodes(hasText(str(MR.strings.decode_image_error))).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.DirectionRight) }
        waitForTag("page 2 ready")
    }

    @Test
    fun `after a click on a control the arrows still turn pages once the controls hide`() = runComposeUiTest {
        openReader(WindowControls(WindowState()))
        waitForTag("page 1 ready")
        // The middle third shows the controls, once told apart from a double click
        onNodeWithTag(READER_TAG).performMouseInput { click(center) }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(QUICK_SETTINGS_TAG).fetchSemanticsNodes().isNotEmpty() }
        // A control in the top bar
        onNodeWithContentDescription(str(MR.strings.action_bookmark)).performMouseInput { click() }
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(QUICK_SETTINGS_TAG).fetchSemanticsNodes().isEmpty() }

        onNodeWithTag(READER_TAG).assertIsFocused()
        onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        waitForTag("page 2")
    }
}

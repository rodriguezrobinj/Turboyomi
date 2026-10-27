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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.window.WindowState
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.reader.BRIGHTNESS_OVERLAY_TAG
import eu.kanade.presentation.reader.COLOR_OVERLAY_TAG
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.browser.EmbeddedBrowser
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
    fun `ctrl plus and minus zoom the page with a note, ctrl+0 goes back, and a new page starts at 100 %`() =
        runComposeUiTest {
            open()
            fun ctrl(key: Key) =
                onNodeWithTag(READER_TAG).performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(key) } }
            fun zoomNote(percent: Int) = "${str(MR.strings.desktop_zoom)}: $percent %"

            waitForTag("page 1 ready")
            ctrl(Key.Plus)
            waitFor(zoomNote(125))
            ctrl(Key.Plus)
            waitFor(zoomNote(156))
            ctrl(Key.Minus)
            waitFor(zoomNote(125))
            ctrl(Key.Zero)
            waitFor(zoomNote(100))

            // Never past 5×
            repeat(10) { ctrl(Key.Plus) }
            waitFor(zoomNote(500))

            // The next page starts over
            onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.DirectionRight) }
            waitForTag("page 2 ready")
            ctrl(Key.Plus)
            waitFor(zoomNote(125))
        }

    @Test
    fun `custom brightness dims the page, a color filter goes over it, and grayscale doesn't break drawing`() =
        runComposeUiTest {
            open()
            val preferences = startTestApp().readerPreferences
            try {
                preferences.customBrightness.set(true)
                preferences.customBrightnessValue.set(-50)
                preferences.colorFilter.set(true)
                preferences.colorFilterValue.set(0x40FF8800)
                preferences.grayscale.set(true)
                preferences.invertedColors.set(true)
                waitForTag("page 1 ready")
                waitForTag(BRIGHTNESS_OVERLAY_TAG)
                onNodeWithTag(COLOR_OVERLAY_TAG).assertExists()

                // A brighter value from an Android backup: the desktop can't brighten the screen, so no overlay
                preferences.customBrightnessValue.set(40)
                waitForIdle()
                onNodeWithTag(BRIGHTNESS_OVERLAY_TAG).assertDoesNotExist()
            } finally {
                preferences.customBrightness.set(false)
                preferences.colorFilter.set(false)
                preferences.grayscale.set(false)
                preferences.invertedColors.set(false)
            }
        }

    @Test
    fun `the quick settings switch the color filter on and show its sliders`() = runComposeUiTest {
        open()
        waitForTag("page 1 ready")
        onNodeWithTag(READER_TAG).performMouseInput { click(center) }
        waitForTag(QUICK_SETTINGS_TAG)
        onNodeWithTag(QUICK_SETTINGS_TAG).performClick()
        try {
            onNodeWithText(str(MR.strings.pref_custom_color_filter)).performScrollTo().performClick()
            waitFor(str(MR.strings.color_filter_r_value))
            startTestApp().readerPreferences.colorFilter.get() shouldBe true
        } finally {
            startTestApp().readerPreferences.colorFilter.set(false)
        }
    }

    @Test
    fun `a wide page split in two reads as its left half, then its right half, then the next page`() =
        runComposeUiTest {
            TestSources.http.images.wide = true
            open()
            val preferences = startTestApp().readerPreferences
            try {
                preferences.dualPageSplitPaged.set(true)
                waitForTag("page 1 ready")
                // Two items for page 1: after one turn page 1 is still there, its other half
                onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.DirectionRight) }
                waitForIdle()
                onNodeWithTag("page 1 ready").assertExists()
                // Page 2 splits once it's decoded, and the reader stays on it
                onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.DirectionRight) }
                waitForTag("page 2 ready")
                Thread.sleep(1_000)
                waitForIdle()
                onNodeWithTag("page 2 ready").assertExists()
            } finally {
                TestSources.http.images.wide = false
                preferences.dualPageSplitPaged.set(false)
            }
        }

    @Test
    fun `the quick settings crop borders and split or turn wide pages, one or the other`() = runComposeUiTest {
        open()
        waitForTag("page 1 ready")
        onNodeWithTag(READER_TAG).performMouseInput { click(center) }
        waitForTag(QUICK_SETTINGS_TAG)
        onNodeWithTag(QUICK_SETTINGS_TAG).performClick()
        val preferences = startTestApp().readerPreferences
        try {
            onNodeWithText(str(MR.strings.pref_crop_borders)).performScrollTo().performClick()
            waitForIdle()
            preferences.cropBorders.get() shouldBe true
            onNodeWithText(str(MR.strings.pref_page_rotate)).performScrollTo().performClick()
            waitForIdle()
            onNodeWithText(str(MR.strings.pref_dual_page_split)).performScrollTo().performClick()
            waitForIdle()
            // Splitting turns the rotation off, as in Mihon
            preferences.dualPageSplitPaged.get() shouldBe true
            preferences.dualPageRotateToFit.get() shouldBe false
        } finally {
            preferences.cropBorders.set(false)
            preferences.dualPageSplitPaged.set(false)
            preferences.dualPageRotateToFit.set(false)
        }
    }

    @Test
    fun `a chapter whose page list fails offers retry and opening it in WebView`() = runComposeUiTest {
        open(failPageList = true)
        waitFor("José")

        // Without the embedded browser, WebView asks to download it first and then opens the chapter
        onNodeWithText(str(MR.strings.action_open_in_web_view)).performClick()
        EmbeddedBrowser.consent?.then?.url.shouldNotBeNull() shouldContain "/c1"
        runOnIdle { EmbeddedBrowser.dismissConsent() }

        onNodeWithText(str(MR.strings.action_retry)).performClick()
        waitForTag("page 1 ready")
    }
}

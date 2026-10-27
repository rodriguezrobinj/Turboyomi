package mihon.desktop.app.reader

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.height
import androidx.compose.ui.window.WindowState
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.main.AppEvents
import mihon.desktop.app.main.AppProviders
import mihon.desktop.app.main.ConnectWindowBack
import mihon.desktop.app.main.Shortcut
import mihon.desktop.app.platform.LocalWindowControls
import mihon.desktop.app.platform.WindowControls
import mihon.desktop.app.reader.viewer.WEBTOON_TAG
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.i18n.MR

@OptIn(ExperimentalTestApi::class)
class WebtoonAndDualTest {
    private object Below : Screen() {
        @Composable
        override fun Content() {
            Text("pantalla anterior")
        }
    }

    /** Opens one of three chapters, the first by default; returns them. */
    private fun ComposeUiTest.open(
        mode: ReadingMode,
        dual: ReaderPreferences.DualPageView,
        chapterIndex: Int = 0,
        scrollStep: Int = 15,
    ): List<Chapter> {
        val graph = startTestApp()
        TestSources.http.pageCount = 6
        val (manga, chapters) = runBlocking { TestLibrary.httpManga(graph) }
        graph.readerPreferences.defaultReadingMode.set(mode.flagValue)
        graph.readerPreferences.dualPageView.set(dual)
        graph.readerPreferences.webtoonScrollStep.set(scrollStep)
        setContent {
            AppProviders(graph) {
                CompositionLocalProvider(LocalWindowControls provides WindowControls(WindowState())) {
                    ConnectWindowBack()
                    Navigator(listOf(Below, ReaderScreen(manga.id, chapters[chapterIndex].id))) {
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
    fun `a short last page in webtoon still marks the chapter read`() = runComposeUiTest {
        val chapters = open(ReadingMode.WEBTOON, ReaderPreferences.DualPageView.NEVER)
        // Shorter than the window: it never gets to the top of the list
        TestSources.images.short += "${chapters[0].url}/5.png"
        waitForTag("page 1 ready")
        // Scrolled down to the bottom of the chapter, pages loading as they come into view; not past it, which would
        // open the next chapter
        fun atBottom(): Boolean {
            val range = onNodeWithTag(
                WEBTOON_TAG,
            ).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
            return range.value() >= range.maxValue()
        }
        repeat(40) {
            if (atBottom() && onAllNodes(hasTestTag("page 6 ready")).fetchSemanticsNodes().isNotEmpty()) return@repeat
            onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.PageDown) }
            waitForIdle()
        }
        atBottom() shouldBe true

        waitUntil(timeoutMillis = 10_000) {
            runBlocking { startTestApp().getChaptersByMangaId.await(chapters[0].mangaId) }.single {
                it.id ==
                    chapters[0].id
            }.read
        }
    }

    @Test
    fun `going up past the top of a webtoon chapter opens the previous one`() = runComposeUiTest {
        val chapters = open(ReadingMode.WEBTOON, ReaderPreferences.DualPageView.NEVER, chapterIndex = 1)
        waitForTag("page 1 ready")
        // Moving to another chapter writes this one's history
        fun leftChapter() = runBlocking { startTestApp().getHistory.await(chapters[1].mangaId) }
            .any { it.chapterId == chapters[1].id }
        leftChapter() shouldBe false

        repeat(3) {
            onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.PageUp) }
            waitForIdle()
        }
        waitUntil(timeoutMillis = 10_000) { leftChapter() }
    }

    @Test
    fun `the previous chapter opens while the next one's page list never comes`() = runComposeUiTest {
        val chapters = open(ReadingMode.WEBTOON, ReaderPreferences.DualPageView.NEVER, chapterIndex = 1)
        TestSources.http.hangingPageLists[chapters[2].url] = false
        waitForTag("page 1 ready")
        onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.MoveEnd) }
        // Near the end the next chapter starts loading in the background, and hangs there
        waitUntil(timeoutMillis = 10_000) { TestSources.http.hangingPageLists[chapters[2].url] == true }

        onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.P) }
        waitForTag("page 1 ready")
        onNodeWithTag("page 1 ready").assertIsDisplayed()
    }

    @Test
    fun `quick presses of the down arrow add up instead of cutting each other short`() = runComposeUiTest {
        open(ReadingMode.WEBTOON, ReaderPreferences.DualPageView.NEVER)
        waitForTag("page 1 ready")
        val viewport = onNodeWithTag(WEBTOON_TAG).getBoundsInRoot().height
        val before = onNodeWithTag("page 1 ready").fetchSemanticsNode().positionInRoot.y

        // A held key: each repeat comes before the last glide has ended
        onNodeWithTag(READER_TAG).performKeyInput { repeat(4) { pressKey(Key.DirectionDown) } }
        waitForIdle()

        val density = onNodeWithTag(WEBTOON_TAG).fetchSemanticsNode().layoutInfo.density.density
        val scrolled = (before - onNodeWithTag("page 1 ready").fetchSemanticsNode().positionInRoot.y) / density
        (scrolled / viewport.value) shouldBe (0.6f plusOrMinus 0.05f)
    }

    @Test
    fun `the down arrow scrolls by the configured share of the window`() = runComposeUiTest {
        open(ReadingMode.WEBTOON, ReaderPreferences.DualPageView.NEVER, scrollStep = 50)
        waitForTag("page 1 ready")
        val viewport = onNodeWithTag(WEBTOON_TAG).getBoundsInRoot().height
        val before = onNodeWithTag("page 1 ready").fetchSemanticsNode().positionInRoot.y

        onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()

        val density = onNodeWithTag(WEBTOON_TAG).fetchSemanticsNode().layoutInfo.density.density
        val scrolled = (before - onNodeWithTag("page 1 ready").fetchSemanticsNode().positionInRoot.y) / density
        (scrolled / viewport.value) shouldBe (0.5f plusOrMinus 0.05f)
    }

    @Test
    fun `the quick settings have the webtoon's side padding and scroll step`() = runComposeUiTest {
        open(ReadingMode.WEBTOON, ReaderPreferences.DualPageView.NEVER)
        waitForTag("page 1 ready")
        onNodeWithTag(READER_TAG).performMouseInput { click(center) }
        waitForTag(QUICK_SETTINGS_TAG)
        onNodeWithTag(QUICK_SETTINGS_TAG).performClick()
        onNodeWithText(str(MR.strings.pref_webtoon_side_padding)).assertExists()
        onNodeWithText(str(MR.strings.desktop_webtoon_scroll_step)).assertExists()
    }

    @Test
    fun `ctrl keys and ctrl wheel change the side padding and the scroll step and say the new value`() =
        runComposeUiTest {
            open(ReadingMode.WEBTOON, ReaderPreferences.DualPageView.NEVER)
            val preferences = startTestApp().readerPreferences
            preferences.webtoonSidePadding.set(10)
            waitForTag("page 1 ready")

            onNodeWithTag(READER_TAG).performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Plus) } }
            waitUntil { preferences.webtoonSidePadding.get() == 5 }
            onNodeWithText("${str(MR.strings.pref_webtoon_side_padding)}: 5 %").assertExists()

            onNodeWithTag(READER_TAG).performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.DirectionUp) } }
            waitUntil { preferences.webtoonScrollStep.get() == 20 }

            // The wheel down with Ctrl zooms out: more padding. Without Ctrl it only scrolls
            onNodeWithTag(READER_TAG).performKeyInput { keyDown(Key.CtrlLeft) }
            onNodeWithTag(READER_TAG).performMouseInput { scroll(1f) }
            onNodeWithTag(READER_TAG).performKeyInput { keyUp(Key.CtrlLeft) }
            waitUntil { preferences.webtoonSidePadding.get() == 10 }
            onNodeWithTag(READER_TAG).performMouseInput { scroll(1f) }
            waitForIdle()
            preferences.webtoonSidePadding.get() shouldBe 10

            // Never past the ends of the settings' range
            onNodeWithTag(READER_TAG).performKeyInput {
                withKeyDown(Key.CtrlLeft) { repeat(4) { pressKey(Key.Plus) } }
            }
            waitForIdle()
            preferences.webtoonSidePadding.get() shouldBe ReaderPreferences.WEBTOON_PADDING_MIN
        }

    @Test
    fun `S scrolls the webtoon by itself, faster with ctrl+shift+up, until the wheel stops it`() =
        runComposeUiTest {
            open(ReadingMode.WEBTOON, ReaderPreferences.DualPageView.NEVER)
            val preferences = startTestApp().readerPreferences
            preferences.webtoonAutoScrollSpeed.set(10)
            waitForTag("page 1 ready")
            fun top() = onNodeWithTag("page 1 ready").fetchSemanticsNode().positionInRoot.y

            // The scrolling runs every frame: with the clock moving by itself the test would never be idle
            mainClock.autoAdvance = false
            val start = top()
            onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.S) }
            mainClock.advanceTimeBy(1_000)
            onNodeWithTag(AUTOSCROLL_TAG).assertExists()
            (top() < start) shouldBe true

            // Faster, with a note, and still running
            onNodeWithTag(READER_TAG).performKeyInput {
                withKeyDown(Key.CtrlLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionUp) } }
            }
            mainClock.advanceTimeBy(100)
            preferences.webtoonAutoScrollSpeed.get() shouldBe 12
            onNodeWithText("${str(MR.strings.desktop_autoscroll)}: 12 %/s").assertExists()
            onNodeWithTag(AUTOSCROLL_TAG).assertExists()

            // A new speed from the quick settings' slider is taken up at once, without stopping
            preferences.webtoonAutoScrollSpeed.set(20)
            mainClock.advanceTimeBy(500)
            onNodeWithTag(AUTOSCROLL_TAG).assertExists()

            // The wheel takes over
            onNodeWithTag(READER_TAG).performMouseInput { scroll(1f) }
            mainClock.advanceTimeBy(100)
            onNodeWithTag(AUTOSCROLL_TAG).assertDoesNotExist()
            mainClock.autoAdvance = true
        }

    @Test
    fun `S stops the automatic scrolling, and so do the arrows and leaving the reader`() = runComposeUiTest {
        open(ReadingMode.WEBTOON, ReaderPreferences.DualPageView.NEVER)
        waitForTag("page 1 ready")
        mainClock.autoAdvance = false

        onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.S) }
        mainClock.advanceTimeBy(300)
        onNodeWithTag(AUTOSCROLL_TAG).assertExists()
        onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.S) }
        mainClock.advanceTimeBy(100)
        onNodeWithTag(AUTOSCROLL_TAG).assertDoesNotExist()

        onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.S) }
        mainClock.advanceTimeBy(300)
        onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.DirectionDown) }
        mainClock.advanceTimeBy(100)
        onNodeWithTag(AUTOSCROLL_TAG).assertDoesNotExist()

        // Leaving the reader: back to the screen below, and nothing keeps scrolling a list that's gone
        onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.S) }
        mainClock.advanceTimeBy(300)
        // Esc reaches the reader as Compose's back event, which ConnectWindowBack forwards
        runOnUiThread { AppEvents.send(Shortcut.Back) }
        mainClock.advanceTimeBy(2_000)
        onAllNodes(hasTestTag(READER_TAG)).fetchSemanticsNodes().isEmpty() shouldBe true
        onAllNodes(hasText("pantalla anterior")).fetchSemanticsNodes().isNotEmpty() shouldBe true
        mainClock.autoAdvance = true
    }

    @Test
    fun `the automatic scrolling stops by itself after the last chapter`() = runComposeUiTest {
        open(ReadingMode.WEBTOON, ReaderPreferences.DualPageView.NEVER, chapterIndex = 2)
        startTestApp().readerPreferences.webtoonAutoScrollSpeed.set(ReaderPreferences.WEBTOON_AUTOSCROLL_MAX)
        waitForTag("page 1 ready")
        onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.MoveEnd) }
        waitForIdle()

        mainClock.autoAdvance = false
        onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.S) }
        mainClock.advanceTimeBy(300)
        onNodeWithTag(AUTOSCROLL_TAG).assertExists()
        // At the end with no chapter after it: stopped once the list hasn't grown for 2 s
        mainClock.advanceTimeBy(8_000)
        onNodeWithTag(AUTOSCROLL_TAG).assertDoesNotExist()
        mainClock.autoAdvance = true
    }

    @Test
    fun `the end key stays on a short last page while the pages above it load`() = runComposeUiTest {
        val chapters = open(ReadingMode.WEBTOON, ReaderPreferences.DualPageView.NEVER)
        TestSources.images.short += "${chapters[0].url}/5.png"
        waitForTag("page 1 ready")
        onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.MoveEnd) }

        // Pages above the short page 6 grow as they load, and the next chapter's pages come in under it: page 6
        // stays in view
        waitForTag("page 6 ready")
        waitForIdle()
        onNodeWithTag("page 6 ready").assertIsDisplayed()
    }

    @Test
    fun `webtoon reads on from the last page of a chapter into the next and the previous one stays above`() =
        runComposeUiTest {
            val chapters = open(ReadingMode.WEBTOON, ReaderPreferences.DualPageView.NEVER)
            fun current() = runBlocking { startTestApp().getHistory.await(chapters[0].mangaId) }
                .any { it.chapterId == chapters[0].id }
            onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.MoveEnd) }
            waitForTag("page 6 ready")

            // The next chapter's first page loads under this one's last, past the way across
            repeat(10) {
                if (current()) return@repeat
                onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.PageDown) }
                waitForIdle()
            }
            waitUntil(timeoutMillis = 10_000) { current() }
            onNodeWithTag("page 1 ready").assertIsDisplayed()

            // Going back up shows the last page of the chapter just left, without a jump
            repeat(3) {
                onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.PageUp) }
                waitForIdle()
            }
            waitForTag("page 6 ready")
            onNodeWithTag("page 6 ready").assertIsDisplayed()
        }

    @Test
    fun `paged reading turns from a chapter's last page to the next one's first and back`() = runComposeUiTest {
        open(ReadingMode.LEFT_TO_RIGHT, ReaderPreferences.DualPageView.NEVER)
        waitForTag("page 1 ready")
        onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.MoveEnd) }
        waitForTag("page 6 ready")

        // The way across, then the next chapter's first page
        repeat(2) {
            onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.DirectionRight) }
            waitForIdle()
        }
        waitForTag("page 1 ready")
        onNodeWithTag("page 1 ready").assertIsDisplayed()

        // And back: the way across, then the last page of the chapter before
        repeat(2) {
            onNodeWithTag(READER_TAG).performKeyInput { pressKey(Key.DirectionLeft) }
            waitForIdle()
        }
        waitForTag("page 6 ready")
        onNodeWithTag("page 6 ready").assertIsDisplayed()
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

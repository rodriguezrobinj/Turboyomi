package mihon.desktop.app.main

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.runComposeUiTest
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import mihon.desktop.app.reader.ReaderAction
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR

@OptIn(ExperimentalTestApi::class)
class ShortcutHelpTest {
    @Test
    fun `every window shortcut and reader action has a line in the help`() {
        val covered = (ShortcutHelp.appRows + ShortcutHelp.readerRows).flatMap { it.covers }.toSet()
        (Shortcut.entries.toSet() - covered) shouldBe emptySet()
        (ReaderAction.entries.toSet() - covered) shouldBe emptySet()
    }

    @Test
    fun `in a reader the help has its viewer's lines only, without one both kinds`() {
        val webtoonOnly = ShortcutHelp.readerRows.first { it.viewer == ReadingMode.ViewerType.Webtoon }
        val pagedOnly = ShortcutHelp.readerRows.first { it.viewer == ReadingMode.ViewerType.Pager }
        ShortcutHelp.readerRowsFor(ReadingMode.ViewerType.Webtoon) shouldContain webtoonOnly
        ShortcutHelp.readerRowsFor(ReadingMode.ViewerType.Webtoon) shouldNotContain pagedOnly
        ShortcutHelp.readerRowsFor(null) shouldContain webtoonOnly
        ShortcutHelp.readerRowsFor(null) shouldContain pagedOnly
    }

    @Test
    fun `F1 opens the help over the library, with both viewers' keys, and back closes it`() = runComposeUiTest {
        val graph = startTestApp()
        setContent { AppContent(graph, crash = null, onRestart = {}) }
        try {
            runOnIdle { AppEvents.send(Shortcut.Help) }
            waitUntil(timeoutMillis = 10_000) {
                onAllNodes(hasTestTag(SHORTCUT_HELP_TAG)).fetchSemanticsNodes().isNotEmpty()
            }
            onAllNodes(hasText(str(MR.strings.desktop_help_autoscroll), substring = true))
                .fetchSemanticsNodes().isNotEmpty() shouldBe true
            onAllNodes(hasText(str(MR.strings.desktop_help_only_webtoon), substring = true))
                .fetchSemanticsNodes().isNotEmpty() shouldBe true

            // Esc reaches the dialog as Compose's back event, which ConnectWindowBack forwards
            runOnIdle { AppEvents.send(Shortcut.Back) }
            waitForIdle()
            ShortcutHelp.open shouldBe false
        } finally {
            ShortcutHelp.open = false
        }
    }
}

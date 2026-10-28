package mihon.desktop.app.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import mihon.desktop.app.browser.EmbeddedBrowser
import mihon.desktop.app.main.AppContent
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import mihon.desktop.core.ContentWarningPrefs
import mihon.domain.extension.model.ContentWarning
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR
import java.net.CookieHandler
import java.net.HttpCookie
import java.net.URI
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalTestApi::class)
class SettingsTest {
    // GitHub's runners compose the first screens far slower than a desktop
    private fun androidx.compose.ui.test.ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 30_000) { onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `allowing 18+ content in the browse settings is saved where the extension loader reads it`() =
        runComposeUiTest {
            val graph = startTestApp()
            setContent { AppContent(graph, crash = null, onRestart = {}) }

            onNodeWithText(str(MR.strings.label_settings)).performClick()
            waitForText(str(MR.strings.browse))
            // "Explorar" is also the rail entry: the settings entry is the last one
            onAllNodesWithText(str(MR.strings.browse)).onLast().performClick()
            waitForText(str(MR.strings.pref_allowed_content_warnings))
            onNodeWithText(str(MR.strings.pref_allowed_content_warnings)).performClick()
            onNodeWithText(str(MR.strings.ext_content_warning_nsfw)).performClick()
            onNodeWithText(str(MR.strings.action_ok)).performClick()
            waitForIdle()

            ContentWarningPrefs.enabled(graph.preferenceStore).get() shouldContain ContentWarning.NSFW
        }

    @Test
    fun `the appearance and advanced settings open`() = runComposeUiTest {
        val graph = startTestApp()
        setContent { AppContent(graph, crash = null, onRestart = {}) }

        onNodeWithText(str(MR.strings.label_settings)).performClick()
        onNodeWithText(str(MR.strings.pref_category_appearance)).performClick()
        // The theme group's title: "App theme" titles a custom preference, which isn't drawn (in Spanish the two match)
        waitForText(str(MR.strings.pref_category_theme))
        // A settings screen has its up arrow, which goes back to the list
        onNodeWithContentDescription(str(MR.strings.action_bar_up_description)).performClick()
        // Advanced sits near the end of a lazy list: on a smaller or denser screen it's composed only once scrolled to
        waitUntil(timeoutMillis = 30_000) { onAllNodes(hasScrollToNodeAction()).fetchSemanticsNodes().isNotEmpty() }
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText(str(MR.strings.pref_category_advanced)))
        onNodeWithText(str(MR.strings.pref_category_advanced)).performClick()
        waitForText(str(MR.strings.desktop_open_logs_folder))
    }

    @Test
    fun `the reader's default mode is chosen in the settings`() = runComposeUiTest {
        val graph = startTestApp()
        setContent { AppContent(graph, crash = null, onRestart = {}) }

        onNodeWithText(str(MR.strings.label_settings)).performClick()
        waitForText(str(MR.strings.pref_category_reader))
        onNodeWithText(str(MR.strings.pref_category_reader)).performClick()
        waitForText(str(MR.strings.pref_viewer_type))
        onNodeWithText(str(MR.strings.pref_viewer_type)).performClick()
        onNodeWithText(str(MR.strings.webtoon_viewer)).performClick()
        waitForIdle()

        graph.readerPreferences.defaultReadingMode.get() shouldBe ReadingMode.WEBTOON.flagValue
    }

    @Test
    fun `the advanced settings offer to install the embedded browser`() = runComposeUiTest {
        val graph = startTestApp()
        setContent { AppContent(graph, crash = null, onRestart = {}) }

        onNodeWithText(str(MR.strings.label_settings)).performClick()
        waitForText(str(MR.strings.pref_category_advanced))
        onNodeWithText(str(MR.strings.pref_category_advanced)).performClick()
        waitForText(str(MR.strings.pref_verbose_logging))
        // The browser group closes a long list (spec 9a §2): it's composed once scrolled to
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText(str(MR.strings.desktop_cef_install)))
        waitForText(str(MR.strings.desktop_cef_status_not_installed))
        onNodeWithText(str(MR.strings.desktop_cef_install)).performClick()

        // The same download dialog as WebView's
        waitForText(str(MR.strings.desktop_cef_download))
        runOnIdle { EmbeddedBrowser.dismissConsent() }
    }

    @Test
    fun `clearing cookies empties the app's cookie store`() = runComposeUiTest {
        val graph = startTestApp()
        val store = (CookieHandler.getDefault() as java.net.CookieManager).cookieStore
        store.add(URI("https://example.com/"), HttpCookie("sesion", "José").apply { path = "/" })
        setContent { AppContent(graph, crash = null, onRestart = {}) }

        onNodeWithText(str(MR.strings.label_settings)).performClick()
        waitForText(str(MR.strings.pref_category_advanced))
        onNodeWithText(str(MR.strings.pref_category_advanced)).performClick()
        waitForText(str(MR.strings.pref_verbose_logging))
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText(str(MR.strings.pref_clear_cookies)))
        onNodeWithText(str(MR.strings.pref_clear_cookies)).performClick()
        waitForIdle()

        store.cookies.shouldBeEmpty()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.openLibrarySettings() {
        onNodeWithText(str(MR.strings.label_settings)).performClick()
        waitForText(str(MR.strings.pref_category_library))
        // "Biblioteca" is also the rail entry: the settings entry is the last one
        onAllNodesWithText(str(MR.strings.pref_category_library)).onLast().performClick()
        waitForText(str(MR.strings.pref_library_update_interval))
    }

    @Test
    fun `the library settings save the update interval where the scheduler reads it`() = runComposeUiTest {
        val graph = startTestApp()
        setContent { AppContent(graph, crash = null, onRestart = {}) }

        openLibrarySettings()
        onNodeWithText(str(MR.strings.pref_library_update_interval)).performClick()
        onNodeWithText(str(MR.strings.update_12hour)).performClick()

        waitUntil(timeoutMillis = 5_000) { graph.libraryPreferences.autoUpdateInterval.get() == 12 }
        graph.libraryUpdateJob.periodicJob().interval() shouldBe 12.hours
        graph.libraryPreferences.autoUpdateInterval.set(0)
    }

    @Test
    fun `close to tray is a library setting`() = runComposeUiTest {
        val graph = startTestApp()
        graph.trayPreferences.traySupported = true
        setContent { AppContent(graph, crash = null, onRestart = {}) }

        openLibrarySettings()
        // The setting is near the end of a lazy list: scroll the list to it first
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText(str(MR.strings.desktop_close_to_tray)))
        onNodeWithText(str(MR.strings.desktop_close_to_tray)).performClick()

        waitUntil(timeoutMillis = 5_000) { graph.trayPreferences.closeToTray.get() }
        graph.trayPreferences.closeToTray.set(false)
    }
}

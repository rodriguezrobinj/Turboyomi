package mihon.desktop.app.tray

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.runComposeUiTest
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.app.main.AppContent
import mihon.desktop.app.main.AppEvents
import mihon.desktop.app.platform.AppMessages
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalTestApi::class)
class TrayTest {
    @Test
    fun `the close button hides to the tray only when it exists and the setting is on`() {
        closeAction(traySupported = true, closeToTray = true) shouldBe CloseAction.Hide
        closeAction(traySupported = true, closeToTray = false) shouldBe CloseAction.Exit
        closeAction(traySupported = false, closeToTray = true) shouldBe CloseAction.Exit
    }

    @Test
    fun `without a tray a notification becomes an in-app message`(): Unit = runBlocking {
        startTestApp()
        TrayNotifier.sink = null
        TrayNotifier.unread = false
        val received = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5.seconds) { AppMessages.messages.first() }
        }

        TrayNotifier.send(Notice("Capítulos nuevos", "Manga José"))

        received.await().text shouldBe "Capítulos nuevos\nManga José"
        TrayNotifier.unread shouldBe false
    }

    @Test
    fun `a tray notification marks the updates as unread until the window opens on them`() {
        val sent = mutableListOf<Notice>()
        TrayNotifier.sink = { sent += it }
        TrayNotifier.unread = false

        TrayNotifier.send(Notice("Capítulos nuevos", "Manga José"))
        TrayNotifier.send(Notice("App", "Sigue en la bandeja", opensUpdates = false))

        sent.map { it.title } shouldBe listOf("Capítulos nuevos", "App")
        TrayNotifier.unread shouldBe true
        TrayNotifier.sink = null
        TrayNotifier.unread = false
    }

    @Test
    fun `asking for the updates tab opens it`() = runComposeUiTest {
        val graph = startTestApp()
        setContent { AppContent(graph, crash = null, onRestart = {}) }
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(hasText(str(MR.strings.label_library))).fetchSemanticsNodes().isNotEmpty()
        }

        AppEvents.openUpdates()

        // The rail label and the updates screen's own title (Mihon's UpdatesAppBar)
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(hasText(str(MR.strings.label_recent_updates))).fetchSemanticsNodes().size >= 2
        }
    }

    @Test
    fun `updating from the tray while an update runs says so, even with the window hidden`(): Unit = runBlocking {
        val graph = startTestApp()
        graph.libraryPreferences.autoUpdateMangaRestrictions.set(emptySet())
        val category = TestLibrary.category(graph)
        TestLibrary.libraryManga(graph, category)
        val gate = CompletableDeferred<Unit>().also { TestSources.updatable.gate = it }
        val sent = mutableListOf<Notice>()
        TrayNotifier.sink = { sent += it }
        graph.libraryUpdateJob.start(category) shouldBe true

        updateFromTray(graph.libraryUpdateJob, graph.context)

        sent.single().message shouldBe str(MR.strings.update_already_running)
        gate.complete(Unit)
        withTimeout(30.seconds) { graph.libraryUpdateJob.awaitIdle() }
        TestSources.updatable.gate = null
        TrayNotifier.sink = null
        TrayNotifier.unread = false
    }
}

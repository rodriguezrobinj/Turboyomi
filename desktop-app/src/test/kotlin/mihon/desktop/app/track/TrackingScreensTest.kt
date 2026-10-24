package mihon.desktop.app.track

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.more.settings.screen.SettingsTrackingScreen
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupTracking
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.main.AppProviders
import mihon.desktop.app.testing.TestBackups
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.TestTrackers
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR
import kotlin.io.path.createTempDirectory

@OptIn(ExperimentalTestApi::class)
class TrackingScreensTest {
    private fun ComposeUiTest.waitForText(text: String) = waitUntil(timeoutMillis = 15_000) {
        onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun `Settings › Tracking lists the trackers without OAuth and none with it`() = runComposeUiTest {
        val graph = startTestApp()
        setContent {
            AppProviders(graph) { Navigator(SettingsTrackingScreen) { DefaultNavigatorScreenTransition(it) } }
        }

        waitForText("Kitsu")
        // The list is lazy: the lower entries exist once scrolled to
        listOf("MangaUpdates", "Komga", "Kavita", "Suwayomi").forEach {
            onNode(hasScrollToNodeAction()).performScrollToNode(hasText(it, substring = true))
        }
        listOf("AniList", "MyAnimeList", "Shikimori", "Bangumi").forEach {
            onAllNodes(hasText(it)).fetchSemanticsNodes().size shouldBe 0
        }
    }

    @Test
    fun `the manga screen's tracking button opens the sheet with the signed-in trackers`() = runComposeUiTest {
        val graph = startTestApp()
        runBlocking { TestTrackers.fake.login("josé", "clave") }
        val manga = runBlocking { TestLibrary.libraryManga(graph, TestLibrary.category(graph)) }
        setContent {
            AppProviders(graph) { Navigator(MangaScreen(manga.id)) { DefaultNavigatorScreenTransition(it) } }
        }

        waitForText(str(MR.strings.manga_tracking_tab))
        onNodeWithText(str(MR.strings.manga_tracking_tab)).performClick()

        // In the sheet, each tracker is its logo, described by the tracker's name
        waitUntil(timeoutMillis = 15_000) {
            onAllNodes(hasContentDescription("Falso José")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `restoring tracks of a signed-out tracker warns about it`(): Unit = runBlocking {
        val graph = startTestApp()
        TestTrackers.fake.logout()
        val file = createTempDirectory("Avisos José").toFile().resolve("tracks.tachibk")
        TestBackups.write(
            file,
            Backup(
                listOf(
                    BackupManga(
                        source = TestSources.updatable.id,
                        url = "/t/1",
                        title = "Con track",
                        tracking = listOf(BackupTracking(syncId = 999, libraryId = 0, title = "x")),
                    ),
                ),
            ),
        )

        graph.backupFileValidator.validate(file).missingTrackers shouldBe listOf("Falso José")
    }
}

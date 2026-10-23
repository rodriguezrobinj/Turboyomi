package mihon.desktop.app.backup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.more.settings.screen.SettingsDataScreen
import eu.kanade.presentation.more.settings.screen.data.RestoreBackupScreen
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.source.model.Page
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.main.AppProviders
import mihon.desktop.app.testing.TestBackups
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR
import kotlin.io.path.createTempDirectory

@OptIn(ExperimentalTestApi::class)
class DataScreensTest {
    private fun ComposeUiTest.waitForText(text: String) = waitUntil(timeoutMillis = 15_000) {
        onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun `Data and storage has Mihon's backup, storage and export sections, without the MIUI warning`() =
        runComposeUiTest {
            val graph = startTestApp()
            setContent {
                AppProviders(graph) { Navigator(SettingsDataScreen) { DefaultNavigatorScreenTransition(it) } }
            }

            waitForText(str(MR.strings.pref_create_backup))
            // The list is lazy: the lower sections exist once scrolled to
            listOf(
                MR.strings.pref_restore_backup,
                MR.strings.pref_backup_interval,
                MR.strings.pref_clear_chapter_cache,
                MR.strings.library_list,
            ).forEach { onNode(hasScrollToNodeAction()).performScrollToNode(hasText(str(it))) }
            onAllNodes(hasText(str(MR.strings.restore_miui_warning))).fetchSemanticsNodes().size shouldBe 0
        }

    @Test
    fun `a running restore shows its progress on the Data screen`() = runComposeUiTest {
        val graph = startTestApp()
        setContent {
            AppProviders(graph) { Navigator(SettingsDataScreen) { DefaultNavigatorScreenTransition(it) } }
        }

        graph.backupNotifier.showRestoreProgress("Restaurado José", 3, 10)

        waitForText(str(MR.strings.restoring_backup))
        waitForText("Restaurado José")
        waitForText("3/10")
    }

    @Test
    fun `clearing the chapter cache empties it`() = runComposeUiTest {
        val graph = startTestApp()
        val (_, chapters) = runBlocking { TestLibrary.httpManga(graph) }
        graph.chapterCache.putPageListToCache(chapters[0], listOf(Page(0, imageUrl = "https://example.org/0.png")))
        setContent {
            AppProviders(graph) { Navigator(SettingsDataScreen) { DefaultNavigatorScreenTransition(it) } }
        }

        waitForText(str(MR.strings.pref_clear_chapter_cache))
        onNodeWithText(str(MR.strings.pref_clear_chapter_cache)).performScrollTo().performClick()

        waitUntil(timeoutMillis = 10_000) {
            runCatching { graph.chapterCache.getPageListFromCache(chapters[0]) }.getOrNull().isNullOrEmpty()
        }
    }

    @Test
    fun `the restore screen lists a backup's missing sources, and an invalid file's error`() = runComposeUiTest {
        val graph = startTestApp()
        val dir = createTempDirectory("Pantalla José").toFile()
        val valid = dir.resolve("valido.tachibk")
        TestBackups.write(
            valid,
            Backup(
                backupManga = listOf(BackupManga(source = 424242L, url = "/perdido", title = "Perdido")),
                backupSources = listOf(BackupSource(name = "Fuente Perdida José", sourceId = 424242L)),
            ),
        )
        val invalid = dir.resolve("invalido.tachibk").apply { writeText("nada") }
        var path by mutableStateOf(valid.path)
        setContent {
            AppProviders(graph) {
                key(path) { Navigator(RestoreBackupScreen(path)) { DefaultNavigatorScreenTransition(it) } }
            }
        }

        waitForText(str(MR.strings.backup_restore_missing_sources))
        waitForText("Fuente Perdida José")
        path = invalid.path
        waitForText(str(MR.strings.invalid_backup_file))
    }
}

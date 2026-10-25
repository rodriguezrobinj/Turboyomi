package mihon.desktop.app.update

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.kotest.matchers.shouldBe
import mihon.desktop.app.main.AppProviders
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR

@OptIn(ExperimentalTestApi::class)
class UpdateUiTest {
    private val release =
        AppRelease("v9.0.0", "Arreglos varios", "https://p", "Turboyomi-9.0.0.msi", "https://m", "https://s")

    @Test
    fun `the dialog shows the version and its notes, and Download starts the update`() = runComposeUiTest {
        val graph = startTestApp()
        val updated = mutableListOf<AppRelease>()
        setContent {
            AppProviders(graph) {
                UpdateDialog(UpdateState.Available(release), onUpdate = {
                    updated += it
                }, onCancel = {}, onDismiss = {})
            }
        }

        onNodeWithText("v9.0.0", substring = true).assertExists()
        onNodeWithText("Arreglos varios", substring = true).assertExists()
        onNodeWithText(str(MR.strings.update_check_confirm)).performClick()
        updated shouldBe listOf(release)
    }

    @Test
    fun `a failed download says why and offers to try again`() = runComposeUiTest {
        val graph = startTestApp()
        val updated = mutableListOf<AppRelease>()
        setContent {
            AppProviders(graph) {
                UpdateDialog(
                    UpdateState.Failed(release, "dañada"),
                    onUpdate = { updated += it },
                    onCancel = {},
                    onDismiss = {},
                )
            }
        }

        onNodeWithText("dañada", substring = true).assertExists()
        onNodeWithText(str(MR.strings.action_retry)).performClick()
        updated shouldBe listOf(release)
    }

    @Test
    fun `while downloading, only Cancel is offered`() = runComposeUiTest {
        val graph = startTestApp()
        var cancelled = false
        setContent {
            AppProviders(graph) {
                UpdateDialog(
                    UpdateState.Downloading(release, 0.5f),
                    onUpdate = {},
                    onCancel = { cancelled = true },
                    onDismiss = {},
                )
            }
        }

        onNodeWithText(str(MR.strings.update_check_confirm)).assertDoesNotExist()
        onNodeWithText(str(MR.strings.action_cancel)).performClick()
        cancelled shouldBe true
    }
}

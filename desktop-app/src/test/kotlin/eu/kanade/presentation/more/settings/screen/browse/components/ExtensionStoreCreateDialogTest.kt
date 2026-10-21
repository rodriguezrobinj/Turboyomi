package eu.kanade.presentation.more.settings.screen.browse.components

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import io.kotest.matchers.shouldBe
import mihon.desktop.app.main.AppProviders
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class ExtensionStoreCreateDialogTest {
    private fun dialogTest(storeIndexUrls: Set<String>, typed: String, expected: List<String>) = runComposeUiTest {
        val created = mutableListOf<String>()
        val graph = startTestApp()
        setContent {
            AppProviders(graph) {
                ExtensionStoreCreateDialog(
                    onDismissRequest = {},
                    onCreate = { created += it },
                    storeIndexUrls = storeIndexUrls,
                    processing = false,
                    errorMessage = null,
                )
            }
        }

        onNode(hasSetTextAction()).performTextInput(typed)
        onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()

        created shouldBe expected
    }

    @Test
    fun `enter adds the typed store`() =
        dialogTest(emptySet(), "https://example.org/index.min.json", listOf("https://example.org/index.min.json"))

    @Test
    fun `enter does nothing when the add button is disabled`() =
        dialogTest(setOf("https://example.org/index.min.json"), "https://example.org/index.min.json", emptyList())
}

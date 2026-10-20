package mihon.desktop.app.source

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import eu.kanade.tachiyomi.source.sourcePreferences
import eu.kanade.tachiyomi.ui.browse.extension.details.SourcePreferencesContent
import io.kotest.matchers.shouldBe
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class SourcePreferencesScreenTest {
    @Test
    fun `each preference is drawn and a switch saves its value`() = runComposeUiTest {
        val graph = startTestApp()
        val source = ConfigurableFakeSource(id = 80)
        setContent {
            CompositionLocalProvider(LocalContext provides graph.context) {
                SourcePreferencesContent(source = source, navigateUp = {})
            }
        }
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasText("Dividir páginas")).fetchSemanticsNodes().isNotEmpty() }
        listOf("Calidad", "Alta", "Dominio", "Idiomas", "No se puede cambiar").forEach {
            onNodeWithText(it, substring = true).assertExists()
        }

        onNodeWithText("Dividir páginas").performClick()
        waitForIdle()

        source.sourcePreferences().getBoolean("split", false) shouldBe true
    }
}

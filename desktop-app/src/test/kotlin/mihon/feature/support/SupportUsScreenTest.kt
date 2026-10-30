package mihon.feature.support

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.runComposeUiTest
import cafe.adriel.voyager.navigator.Navigator
import mihon.desktop.app.main.AppProviders
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import mihon.desktop.core.AppDirs
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR

@OptIn(ExperimentalTestApi::class)
class SupportUsScreenTest {
    @Test
    fun `the support screen says the donations go to Mihon`() = runComposeUiTest {
        val graph = startTestApp()
        setContent { AppProviders(graph) { Navigator(SupportUsScreen()) } }

        val note = str(MR.strings.desktop_support_mihon_note, AppDirs.APP_NAME)
        waitUntil(timeoutMillis = 30_000) { onAllNodes(hasText(note)).fetchSemanticsNodes().isNotEmpty() }
        waitUntil(timeoutMillis = 30_000) {
            onAllNodes(hasText(str(MR.strings.supportUsScreen_donationPlatform_patreon))).fetchSemanticsNodes()
                .isNotEmpty()
        }
    }
}

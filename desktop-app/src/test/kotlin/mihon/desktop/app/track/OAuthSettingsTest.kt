package mihon.desktop.app.track

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.more.settings.screen.SettingsTrackingScreen
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import mihon.desktop.app.main.AppProviders
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR

@OptIn(ExperimentalTestApi::class)
class OAuthSettingsTest {
    private val graph = TrackerKeys.withTestKeys { startTestApp() }

    @Test
    fun `with keys Settings › Tracking lists the OAuth trackers, and warns when the protocol isn't registered`() =
        runComposeUiTest {
            ProtocolRegistrar.registered = false
            setContent {
                AppProviders(graph) { Navigator(SettingsTrackingScreen) { DefaultNavigatorScreenTransition(it) } }
            }

            waitUntil(timeoutMillis = 15_000) {
                onAllNodes(hasText("MyAnimeList")).fetchSemanticsNodes().isNotEmpty()
            }
            listOf("AniList", "Shikimori", "Bangumi", "Hikka", "Comick", "MangaBaka").forEach {
                onNode(hasScrollToNodeAction()).performScrollToNode(hasText(it, substring = true))
            }
            onNode(hasScrollToNodeAction()).performScrollToNode(
                hasText(str(MR.strings.desktop_tracker_protocol_failed), substring = true),
            )
            ProtocolRegistrar.registered = null
        }
}

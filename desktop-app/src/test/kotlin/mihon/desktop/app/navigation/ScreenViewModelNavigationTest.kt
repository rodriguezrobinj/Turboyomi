package mihon.desktop.app.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import eu.kanade.presentation.util.Screen
import io.kotest.matchers.shouldBe
import mihon.desktop.app.main.AppProviders
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class ScreenViewModelNavigationTest {
    class Holder : ViewModel() {
        var cleared = false
        override fun onCleared() {
            cleared = true
        }
    }

    private val seen = mutableListOf<Holder>()

    private inner class Below : Screen() {
        @Composable
        override fun Content() {
            val holder = viewModel { Holder() }
            seen += holder
            Text("below")
        }
    }

    private class Above : Screen() {
        @Composable
        override fun Content() {
            Text("above")
        }
    }

    @Test
    fun `the screen below keeps its view model while another one is on top`() = runComposeUiTest {
        val graph = startTestApp()
        lateinit var navigator: Navigator
        setContent {
            AppProviders(graph) {
                Navigator(Below()) {
                    navigator = it
                    DefaultNavigatorScreenTransition(it)
                }
            }
        }
        fun waitFor(text: String) = waitUntil(timeoutMillis = 5_000) {
            onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }

        waitFor("below")
        runOnIdle { navigator.push(Above()) }
        waitFor("above")
        runOnIdle { navigator.pop() }
        waitFor("below")

        seen.distinct().size shouldBe 1
        seen.first().cleared shouldBe false
    }
}

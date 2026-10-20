package androidx.activity.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class BackHandlerTest {
    @Test
    fun `back reaches the innermost enabled handler, then the outer one`() = runComposeUiTest {
        val calls = mutableListOf<String>()
        var searchOpen by mutableStateOf(true)
        setContent {
            BackHandler { calls += "screen" }
            BackHandler(enabled = searchOpen) {
                calls += "search"
                searchOpen = false
            }
        }

        BackPressDispatcher.dispatch() shouldBe true
        waitForIdle()
        BackPressDispatcher.dispatch() shouldBe true

        calls shouldBe listOf("search", "screen")
    }

    @Test
    fun `nothing handles back once the handlers leave the composition`() = runComposeUiTest {
        var shown by mutableStateOf(true)
        setContent { if (shown) BackHandler { } }
        shown = false
        waitForIdle()

        BackPressDispatcher.dispatch() shouldBe false
    }
}

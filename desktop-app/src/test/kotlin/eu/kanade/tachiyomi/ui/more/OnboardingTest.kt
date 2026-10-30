package eu.kanade.tachiyomi.ui.more

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.main.AppContent
import mihon.desktop.app.main.skipOnboardingForExistingUsers
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR

@OptIn(ExperimentalTestApi::class)
class OnboardingTest {
    private fun androidx.compose.ui.test.ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 30_000) { onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `a first start walks through the onboarding to the library`() = runComposeUiTest {
        val graph = startTestApp()
        val shown = graph.basePreferences.shownOnboardingFlow
        shown.set(false)
        try {
            setContent { AppContent(graph, crash = null, onRestart = {}) }

            waitForText(str(MR.strings.onboarding_heading))
            repeat(2) {
                onNodeWithText(str(MR.strings.onboarding_action_next)).performClick()
                waitForIdle()
            }
            onNodeWithText(str(MR.strings.onboarding_action_finish)).performClick()

            waitUntil(timeoutMillis = 30_000) { shown.get() }
            waitUntil(timeoutMillis = 30_000) {
                onAllNodes(hasText(str(MR.strings.onboarding_heading))).fetchSemanticsNodes().isEmpty()
            }
        } finally {
            shown.set(true)
        }
    }

    @Test
    fun `someone with a library already knows the app and skips it`() {
        val graph = startTestApp()
        val shown = graph.basePreferences.shownOnboardingFlow
        try {
            runBlocking {
                TestLibrary.libraryManga(graph, TestLibrary.category(graph))
                shown.delete()
                skipOnboardingForExistingUsers(graph)
            }
            shown.get() shouldBe true
        } finally {
            shown.set(true)
        }
    }
}

package mihon.desktop.app.upcoming

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import eu.kanade.tachiyomi.source.model.SManga
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import mihon.desktop.app.main.AppContent
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import mihon.feature.upcoming.UpcomingUIModel
import mihon.feature.upcoming.UpcomingViewModel
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalTestApi::class)
class UpcomingTest {
    @Test
    fun `upcoming groups the library by expected day, without completed or undated manga`(): Unit = runBlocking {
        val graph = startTestApp()
        val category = TestLibrary.category(graph)
        val now = Clock.System.now()
        val tomorrow = TestLibrary.ongoingManga(graph, category, now + 1.days)
        val later = TestLibrary.ongoingManga(graph, category, now + 3.days)
        val completed = TestLibrary.ongoingManga(graph, category, now + 1.days, status = SManga.COMPLETED.toLong())
        val undated = TestLibrary.ongoingManga(graph, category, nextUpdate = null)
        val viewModel = UpcomingViewModel(graph.getUpcomingManga, graph.getCategories, graph.upcomingPreferences)

        val state = withTimeout(10.seconds) {
            viewModel.state.first { s -> s.items.any { it is UpcomingUIModel.Item && it.manga.id == later.id } }
        }

        val ids = state.items.filterIsInstance<UpcomingUIModel.Item>().map { it.manga.id }
        ids shouldContain tomorrow.id
        ids shouldNotContain completed.id
        ids shouldNotContain undated.id
        val zone = TimeZone.currentSystemDefault()
        state.events.keys shouldContain (now + 1.days).toLocalDateTime(zone).date
        state.events.keys shouldContain (now + 3.days).toLocalDateTime(zone).date
        (ids.indexOf(tomorrow.id) < ids.indexOf(later.id)) shouldBe true
    }

    @Test
    fun `the calendar button in Updates opens Upcoming`() = runComposeUiTest {
        val graph = startTestApp()
        setContent { AppContent(graph, crash = null, onRestart = {}) }

        waitUntil(timeoutMillis = 15_000) {
            onAllNodes(hasText(str(MR.strings.label_recent_updates))).fetchSemanticsNodes().isNotEmpty()
        }
        onAllNodesWithText(str(MR.strings.label_recent_updates)).onFirst().performClick()
        waitUntil(timeoutMillis = 15_000) {
            onAllNodes(androidx.compose.ui.test.hasContentDescription(str(MR.strings.action_view_upcoming)))
                .fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithContentDescription(str(MR.strings.action_view_upcoming)).performClick()
        waitUntil(timeoutMillis = 15_000) {
            onAllNodes(hasText(str(MR.strings.label_upcoming))).fetchSemanticsNodes().isNotEmpty()
        }
    }
}

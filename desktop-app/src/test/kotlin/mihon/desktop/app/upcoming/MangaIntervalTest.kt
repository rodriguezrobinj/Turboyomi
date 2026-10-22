package mihon.desktop.app.upcoming

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import mihon.desktop.app.main.AppProviders
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.plural
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import mihon.feature.upcoming.UpcomingUIModel
import mihon.feature.upcoming.UpcomingViewModel
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalTestApi::class)
class MangaIntervalTest {
    @Test
    fun `fixing 3 days gives an undated manga a next update in Upcoming, and Default goes back to automatic`(): Unit =
        runBlocking {
            val graph = startTestApp()
            val manga = TestLibrary.ongoingManga(graph, TestLibrary.category(graph), nextUpdate = null)

            // What MangaViewModel.setFetchInterval does for 3 days: custom intervals are negative
            graph.updateManga.awaitUpdateFetchInterval(manga.copy(fetchInterval = -3))
            val fixed = graph.getManga.await(manga.id)!!
            fixed.fetchInterval shouldBe -3
            fixed.nextUpdate.shouldNotBeNull()
            val viewModel = UpcomingViewModel(graph.getUpcomingManga, graph.getCategories, graph.upcomingPreferences)
            withTimeout(10.seconds) {
                viewModel.state.first { s -> s.items.any { it is UpcomingUIModel.Item && it.manga.id == manga.id } }
            }

            graph.updateManga.awaitUpdateFetchInterval(fixed.copy(fetchInterval = 0))
            graph.getManga.await(manga.id)!!.fetchInterval shouldBeGreaterThanOrEqual 0
        }

    /** The interval button's text, as MangaInfoHeader computes it from the stored next update. */
    private fun intervalLabel(graph: TestAppGraph, mangaId: Long): String {
        val next = runBlocking { graph.getManga.await(mangaId)!!.expectedNextUpdate }
        val days = next?.let { Clock.System.now().daysUntil(it, TimeZone.currentSystemDefault()) }
            ?: return str(MR.strings.not_applicable)
        return if (days <=
            0
        ) {
            str(MR.strings.manga_interval_expected_update_soon)
        } else {
            plural(MR.plurals.day, days, days)
        }
    }

    @Test
    fun `the interval button of a library manga opens the dialog with the day picker`() = runComposeUiTest {
        val graph = startTestApp()
        val manga = runBlocking { TestLibrary.libraryManga(graph, TestLibrary.category(graph)) }
        setContent {
            AppProviders(graph) { Navigator(MangaScreen(manga.id)) { DefaultNavigatorScreenTransition(it) } }
        }

        // Opening the manga refreshes it from the source, which may move its next update: read the label each time
        waitUntil(timeoutMillis = 15_000) {
            onAllNodes(hasText(intervalLabel(graph, manga.id))).fetchSemanticsNodes().isNotEmpty()
        }
        onAllNodesWithText(intervalLabel(graph, manga.id)).onFirst().performClick()
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(hasText(str(MR.strings.pref_library_update_smart_update))).fetchSemanticsNodes().isNotEmpty()
        }
        // The picker shows only when the dialog can change the interval (no release-build gate on desktop)
        onAllNodes(hasText(str(MR.strings.manga_interval_custom_amount))).fetchSemanticsNodes().size shouldBe 1
    }
}

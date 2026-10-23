package mihon.desktop.app.library

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import eu.kanade.presentation.util.relativeTimeSpan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.app.main.AppContent
import mihon.desktop.app.reader.READER_TAG
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.TestSources
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.i18n.MR
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalTestApi::class)
class UpdatesTabTest {
    @Test
    fun `a new chapter is listed, opening the tab clears the badge count, and clicking it opens the reader`() =
        runComposeUiTest {
            val graph = startTestApp()
            graph.libraryPreferences.autoUpdateMangaRestrictions.set(emptySet())
            val category = runBlocking { TestLibrary.category(graph) }
            val manga = runBlocking { TestLibrary.libraryManga(graph, category) }
            TestSources.updatable.chapters[manga.url] = 4
            runBlocking {
                graph.libraryUpdateJob.start(category)
                withTimeout(30.seconds) { graph.libraryUpdateJob.awaitIdle() }
            }
            setContent { AppContent(graph, crash = null, onRestart = {}) }

            onNodeWithText(str(MR.strings.label_recent_updates)).performClick()
            waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("Capítulo 4")).fetchSemanticsNodes().isNotEmpty() }
            waitUntil(timeoutMillis = 10_000) { graph.libraryPreferences.newUpdatesCount.get() == 0 }

            onAllNodesWithText("Capítulo 4").onFirst().performClick()
            waitUntil(timeoutMillis = 15_000) { onAllNodes(hasTestTag(READER_TAG)).fetchSemanticsNodes().isNotEmpty() }
        }

    @Test
    fun `the last update reads like Android's, in whole minutes, hours or days`() {
        val graph = startTestApp()
        val context = graph.context
        val now = 1_800_000_000_000L
        fun ago(millis: Long) = relativeTimeSpan(context, now - millis, now)

        ago(5.minutes.inWholeMilliseconds) shouldBe
            context.pluralStringResource(MR.plurals.desktop_minutes_ago, 5, 5)
        ago((2.hours + 5.minutes).inWholeMilliseconds) shouldBe
            context.pluralStringResource(MR.plurals.desktop_hours_ago, 2, 2)
        ago(26.hours.inWholeMilliseconds) shouldBe context.pluralStringResource(MR.plurals.relative_time, 1, 1)
        ago(3.days.inWholeMilliseconds) shouldBe context.pluralStringResource(MR.plurals.relative_time, 3, 3)
    }
}

package mihon.desktop.app.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeDown
import eu.kanade.presentation.manga.components.MangaToolbar
import io.kotest.matchers.shouldBe
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.PullRefresh

@OptIn(ExperimentalTestApi::class)
class PullRefreshTest {
    @Test
    fun `pulling past the top of a list doesn't refresh it, by wheel or by drag`() = runComposeUiTest {
        var refreshes = 0
        setContent {
            PullRefresh(refreshing = false, enabled = true, onRefresh = { refreshes++ }) {
                LazyColumn(Modifier.fillMaxSize().testTag("list")) {
                    items(50) { Text("Item $it") }
                }
            }
        }

        // Already at the top: a long upward wheel, as when scrolling back up a chapter list
        repeat(10) {
            onNodeWithTag("list").performMouseInput {
                moveTo(center)
                scroll(-500f)
            }
        }
        onNodeWithTag("list").performTouchInput { swipeDown() }
        mainClock.advanceTimeBy(3_000)

        refreshes shouldBe 0
    }

    @Test
    fun `the manga screen's refresh is a button on its bar, not in the overflow menu`() = runComposeUiTest {
        var refreshes = 0
        val graph = startTestApp()
        setContent {
            AppProviders(graph) {
                MangaToolbar(
                    title = "Manga",
                    hasFilters = false,
                    navigateUp = {},
                    onClickFilter = {},
                    onClickShare = null,
                    onClickDownload = null,
                    onClickEditCategory = null,
                    onClickRefresh = { refreshes++ },
                    onClickMigrate = null,
                    onClickEditNotes = null,
                    actionModeCounter = 0,
                    onCancelActionMode = {},
                    onSelectAll = {},
                    onInvertSelection = {},
                    titleAlphaProvider = { 1f },
                    backgroundAlphaProvider = { 1f },
                )
            }
        }

        onNodeWithContentDescription(str(MR.strings.action_webview_refresh)).performClick()

        refreshes shouldBe 1
    }
}

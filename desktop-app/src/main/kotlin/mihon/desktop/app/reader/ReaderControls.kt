package mihon.desktop.app.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import eu.kanade.presentation.reader.ReaderPageIndicator
import eu.kanade.presentation.reader.appbars.ReaderTopBar
import eu.kanade.presentation.reader.components.ChapterNavigator
import eu.kanade.presentation.reader.components.ChapterNavigatorType
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Settings
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

const val QUICK_SETTINGS_TAG = "reader quick settings"

/** Top bar (manga and chapter), bottom bar (page slider, chapters, settings) and the page number (spec 2a §4). */
@Composable
fun ReaderControls(
    state: ReaderViewModel.State,
    mode: ReadingMode,
    onBack: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenInBrowser: (() -> Unit)?,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onPageIndexChange: (Int) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val chapters = state.viewerChapters
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            state.menuVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            ReaderTopBar(
                mangaTitle = state.manga?.title,
                chapterTitle = state.currentChapter?.chapter?.name,
                navigateUp = onBack,
                bookmarked = state.bookmarked,
                onToggleBookmarked = onToggleBookmark,
                onOpenInWebView = null,
                onOpenInBrowser = onOpenInBrowser,
                onShare = null,
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer),
            )
        }
        AnimatedVisibility(
            state.menuVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(Modifier.fillMaxWidth()) {
                ChapterNavigator(
                    type = if (mode ==
                        ReadingMode.RIGHT_TO_LEFT
                    ) {
                        ChapterNavigatorType.HORIZONTAL_RTL
                    } else {
                        ChapterNavigatorType.HORIZONTAL_LTR
                    },
                    onNextChapter = onNextChapter,
                    enabledNext = chapters?.nextChapter != null,
                    onPreviousChapter = onPreviousChapter,
                    enabledPrevious = chapters?.prevChapter != null,
                    currentPage = state.currentPage.coerceAtLeast(1),
                    totalPages = state.totalPages.coerceAtLeast(1),
                    onPageIndexChange = onPageIndexChange,
                    onPageIndexChangeFinished = {},
                )
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)) {
                    IconButton(onClick = onOpenSettings, modifier = Modifier.testTag(QUICK_SETTINGS_TAG)) {
                        Icon(
                            MaterialSymbols.Rounded.Settings,
                            contentDescription = stringResource(MR.strings.action_settings),
                        )
                    }
                }
            }
        }
        if (!state.menuVisible && state.totalPages > 0) {
            ReaderPageIndicator(
                currentPage = state.currentPage,
                totalPages = state.totalPages,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

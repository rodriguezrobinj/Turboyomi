package eu.kanade.tachiyomi.ui.updates

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.updates.UpdateScreen
import eu.kanade.presentation.updates.UpdatesFilterDialog
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.updates.UpdatesViewModel.Event
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import mihon.desktop.app.main.AppEvents
import mihon.desktop.app.reader.ReaderScreen
import mihon.desktop.app.tray.TrayNotifier
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Autorenew
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

data object UpdatesTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            return TabOptions(
                index = 1u,
                title = stringResource(MR.strings.label_recent_updates),
                // desktop: no Android animated drawables
                icon = rememberVectorPainter(MaterialSymbols.Rounded.Autorenew),
            )
        }

    // desktop: no onReselect, the download queue arrives in subproject 4

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = metroViewModel<UpdatesViewModel>()
        val settingsViewModel = metroViewModel<UpdatesSettingsViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()

        UpdateScreen(
            state = state,
            snackbarHostState = viewModel.snackbarHostState,
            lastUpdated = viewModel.lastUpdated,
            onClickCover = { item -> navigator.push(MangaScreen(item.update.mangaId)) },
            onSelectAll = viewModel::toggleAllSelection,
            onInvertSelection = viewModel::invertSelection,
            onUpdateLibrary = viewModel::updateLibrary,
            onDownloadChapter = null, // desktop: downloads arrive in subproject 4
            onMultiBookmarkClicked = viewModel::bookmarkUpdates,
            onMultiMarkAsReadClicked = viewModel::markUpdatesRead,
            onMultiDeleteClicked = null, // desktop: downloads arrive in subproject 4
            onUpdateSelected = viewModel::toggleSelection,
            onOpenChapter = {
                // desktop: the reader is a screen, not an Activity
                navigator.push(ReaderScreen(it.update.mangaId, it.update.chapterId))
            },
            onCalendarClicked = null, // desktop: Upcoming arrives in 3b
            onFilterClicked = viewModel::showFilterDialog,
            hasActiveFilters = state.hasActiveFilters,
        )

        val onDismissDialog = { viewModel.setDialog(null) }
        when (state.dialog) {
            is UpdatesViewModel.Dialog.FilterSheet -> {
                UpdatesFilterDialog(
                    onDismissRequest = onDismissDialog,
                    viewModel = settingsViewModel,
                )
            }
            // desktop: no delete confirmation until downloads (subproject 4)
            else -> {}
        }

        LaunchedEffect(Unit) {
            // desktop: F5 updates the whole library, Ctrl+A selects every update (spec 3a §3 C)
            launch { AppEvents.refresh.collect { viewModel.updateLibrary() } }
            launch { AppEvents.selectAll.collect { viewModel.toggleAllSelection(true) } }
            viewModel.events.collectLatest { event ->
                when (event) {
                    Event.InternalError -> viewModel.snackbarHostState.showSnackbar(
                        context.stringResource(MR.strings.internal_error),
                    )
                    is Event.LibraryUpdateTriggered -> {
                        val msg = if (event.started) {
                            MR.strings.updating_library
                        } else {
                            MR.strings.update_already_running
                        }
                        viewModel.snackbarHostState.showSnackbar(context.stringResource(msg))
                    }
                }
            }
        }

        // desktop: no bottom bar to hide and no Activity to report ready
        DisposableEffect(Unit) {
            viewModel.resetNewUpdatesCount()
            // desktop: the updates were seen, so a later Open from the tray doesn't jump here again
            TrayNotifier.unread = false

            onDispose {
                viewModel.resetNewUpdatesCount()
            }
        }
    }
}

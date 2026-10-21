package mihon.desktop.app.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import kotlinx.coroutines.launch
import mihon.desktop.app.platform.ExitHooks
import mihon.desktop.app.platform.LocalWindowControls
import mihon.desktop.app.reader.image.DecodedPageCache
import mihon.desktop.app.reader.viewer.PagerViewer
import mihon.desktop.app.reader.viewer.ViewerController
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.collectAsState

const val READER_TAG = "reader"

/** The reader takes the whole window (spec 2a §4, "Pantalla"): a screen pushed on top of everything else. */
class ReaderScreen(private val mangaId: Long, private val chapterId: Long) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val window = LocalWindowControls.current
        val viewModel = assistedMetroViewModel<ReaderViewModel, ReaderViewModel.Factory> { create(mangaId, chapterId) }

        // Esc: out of fullscreen first, then out of the reader
        BackHandler { if (window?.isFullscreen == true) window.exitFullscreen() else navigator.pop() }
        DisposableEffect(viewModel) {
            val flush: suspend () -> Unit = { viewModel.flush() }
            ExitHooks.add(flush)
            onDispose {
                ExitHooks.remove(flush)
                window?.exitFullscreen()
                viewModel.saveHistory()
            }
        }
        ReaderContent(viewModel, onClose = navigator::pop)
    }
}

@Composable
fun ReaderContent(viewModel: ReaderViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val preferences = viewModel.readerPreferences
    val window = LocalWindowControls.current
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    var controller by remember { mutableStateOf<ViewerController?>(null) }
    val cache = remember { DecodedPageCache() }
    val mode = remember(state.manga) { viewModel.getMangaReadingMode() }
    val scale by preferences.imageScaleType.collectAsState()
    val background by preferences.readerTheme.collectAsState()
    val dualView by preferences.dualPageView.collectAsState()
    var shifted by remember { mutableStateOf(false) }

    fun perform(action: ReaderAction) {
        when (action) {
            ReaderAction.ToggleFullscreen -> window?.toggleFullscreen()
            ReaderAction.ToggleMenu -> viewModel.showMenus(!state.menuVisible)
            ReaderAction.NextChapter -> scope.launch { viewModel.loadNextChapter() }
            ReaderAction.PreviousChapter -> scope.launch { viewModel.loadPreviousChapter() }
            else -> scope.launch { controller?.perform(action) }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(ReaderBackground.fromPreference(background).color())
            .testTag(READER_TAG)
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val action =
                    readerActionFor(event.key, event.isShiftPressed, event.isCtrlPressed, event.isAltPressed, mode)
                        ?: return@onKeyEvent false
                perform(action)
                true
            },
    ) {
        val chapters = state.viewerChapters
        when {
            state.initError != null -> Unit // Task 7: the error screen with retry and open in browser
            chapters == null -> LoadingScreen()
            else -> key(chapters.currChapter, mode) {
                PagerViewer(
                    chapters = chapters,
                    mode = mode,
                    dual = isDualPage(dualView),
                    shifted = shifted,
                    scale = ImageScale.fromPreference(scale),
                    cache = cache,
                    onPageSelected = viewModel::onPageSelected,
                    onPastEnd = { forward ->
                        scope.launch { if (forward) viewModel.loadNextChapter() else viewModel.loadPreviousChapter() }
                    },
                    onTap = ::perform,
                    onRetry = viewModel::retryPage,
                    onController = { controller = it },
                )
            }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** Mihon's dualPageView: never, always, or when the window is wider than tall. */
@Composable
private fun isDualPage(view: ReaderPreferences.DualPageView): Boolean = when (view) {
    ReaderPreferences.DualPageView.NEVER -> false
    ReaderPreferences.DualPageView.ALWAYS -> true
    ReaderPreferences.DualPageView.WIDE -> LocalWindowInfo.current.containerSize.let { it.width > it.height }
}

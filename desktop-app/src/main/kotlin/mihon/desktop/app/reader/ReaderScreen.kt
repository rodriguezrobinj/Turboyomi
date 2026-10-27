package mihon.desktop.app.reader

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mihon.desktop.app.browser.EmbeddedBrowser
import mihon.desktop.app.platform.ExitHooks
import mihon.desktop.app.platform.FileDialogs
import mihon.desktop.app.platform.LocalWindowControls
import mihon.desktop.app.reader.image.DecodedPageCache
import mihon.desktop.app.reader.viewer.PagerViewer
import mihon.desktop.app.reader.viewer.ViewerController
import mihon.desktop.app.reader.viewer.WebtoonViewer
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Public
import mihon.icons.materialsymbols.rounded.Refresh
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.collectAsState
import kotlin.time.Duration.Companion.seconds

const val READER_TAG = "reader"

/** The reader takes the whole window (spec 2a §4, "Pantalla"): a screen pushed on top of everything else. */
class ReaderScreen(private val mangaId: Long, private val chapterId: Long) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = assistedMetroViewModel<ReaderViewModel, ReaderViewModel.Factory> { create(mangaId, chapterId) }

        BackHandler { navigator.pop() }
        DisposableEffect(viewModel) {
            val flush: suspend () -> Unit = { viewModel.flush() }
            ExitHooks.add(flush)
            onDispose {
                ExitHooks.remove(flush)
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
    var showSettings by remember { mutableStateOf(false) }
    // What a Ctrl key or Ctrl+wheel just changed, and when: shown for a moment over the page
    var notice by remember { mutableStateOf<Pair<String, Long>?>(null) }
    val context = LocalContext.current
    // Asks the source, so once per chapter rather than on every recomposition
    val chapterUrl = remember(state.currentChapter, state.source, state.initError) { viewModel.getChapterUrl() }

    // The controls hide after 3 s without moving the mouse (spec 2a §4)
    var lastMouseMove by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.menuVisible, lastMouseMove) {
        if (state.menuVisible) {
            delay(3.seconds)
            viewModel.showMenus(false)
        }
    }

    fun adjust(action: ReaderAction) {
        val padding = action == ReaderAction.ZoomIn || action == ReaderAction.ZoomOut
        val preference = if (padding) preferences.webtoonSidePadding else preferences.webtoonScrollStep
        val range = if (padding) {
            ReaderPreferences.WEBTOON_PADDING_MIN..ReaderPreferences.WEBTOON_PADDING_MAX
        } else {
            ReaderPreferences.WEBTOON_SCROLL_STEP_MIN..ReaderPreferences.WEBTOON_SCROLL_STEP_MAX
        }
        // Zooming in narrows the padding
        val sign = if (action == ReaderAction.ZoomOut || action == ReaderAction.ScrollStepUp) 1 else -1
        val value = (preference.get() + sign * ReaderPreferences.WEBTOON_KEY_STEP).coerceIn(range)
        preference.set(value)
        val title = if (padding) MR.strings.pref_webtoon_side_padding else MR.strings.desktop_webtoon_scroll_step
        notice = "${context.stringResource(title)}: $value %" to System.nanoTime()
    }

    fun perform(action: ReaderAction) {
        when (action) {
            ReaderAction.ToggleMenu -> viewModel.showMenus(!state.menuVisible)
            ReaderAction.ZoomIn, ReaderAction.ZoomOut, ReaderAction.ScrollStepUp, ReaderAction.ScrollStepDown ->
                adjust(action)
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
            .onPointerEvent(PointerEventType.Move) { lastMouseMove = System.nanoTime() }
            // Before the webtoon list sees it, which would scroll
            .onPointerEvent(PointerEventType.Scroll, PointerEventPass.Initial) { event ->
                val delta = event.changes.first().scrollDelta.y
                if (event.keyboardModifiers.isCtrlPressed && mode.type == ReadingMode.ViewerType.Webtoon &&
                    delta != 0f
                ) {
                    adjust(if (delta < 0) ReaderAction.ZoomIn else ReaderAction.ZoomOut)
                    event.changes.forEach { it.consume() }
                }
            }
            // A clicked control would keep the focus, and the keys with it, until the controls hide and drop it
            .onPointerEvent(PointerEventType.Release) { focus.requestFocus() }
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
            state.initError != null -> {
                val error = state.initError!!
                val url = chapterUrl
                EmptyScreen(
                    message = with(context) { error.formattedMessage },
                    actions = listOfNotNull(
                        EmptyScreenAction(MR.strings.action_retry, MaterialSymbols.Rounded.Refresh) {
                            viewModel.retryInit()
                        },
                        url?.let {
                            EmptyScreenAction(MR.strings.action_open_in_web_view, MaterialSymbols.Rounded.Public) {
                                EmbeddedBrowser.open(context, it, (state.source as? HttpSource)?.headers)
                            }
                        },
                    ),
                )
            }
            chapters == null -> LoadingScreen()
            // Not per chapter: the viewers go on from one chapter into the next (see viewerItems)
            else -> key(mode) {
                if (mode.type == ReadingMode.ViewerType.Webtoon) {
                    val sidePadding by preferences.webtoonSidePadding.collectAsState()
                    val scrollStep by preferences.webtoonScrollStep.collectAsState()
                    WebtoonViewer(
                        chapters = chapters,
                        gap = mode == ReadingMode.CONTINUOUS_VERTICAL,
                        sidePaddingPercent = sidePadding,
                        scrollStepPercent = scrollStep,
                        cache = cache,
                        onPageSelected = viewModel::onPageSelected,
                        onReachedEnd = { scope.launch { viewModel.loadNextChapter(from = chapters.currChapter) } },
                        onReachedStart = {
                            scope.launch { viewModel.loadPreviousChapter(from = chapters.currChapter) }
                        },
                        onTap = ::perform,
                        onRetry = viewModel::retryPage,
                        onController = { controller = it },
                        jump = state.chapterJumps,
                        onSave = { page -> savePage(context, state, page) },
                        onCopy = { page ->
                            scope.launch(Dispatchers.IO) {
                                page.stream?.let { PageActions.copyImage(it().use { s -> s.readBytes() }) }
                                context.toast(MR.strings.desktop_image_copied)
                            }
                        },
                    )
                } else {
                    PagerViewer(
                        chapters = chapters,
                        mode = mode,
                        dual = isDualPage(dualView),
                        shifted = shifted,
                        scale = ImageScale.fromPreference(scale),
                        cache = cache,
                        onPageSelected = viewModel::onPageSelected,
                        onPastEnd = { forward ->
                            scope.launch {
                                val from = chapters.currChapter
                                if (forward) viewModel.loadNextChapter(from) else viewModel.loadPreviousChapter(from)
                            }
                        },
                        onTap = ::perform,
                        onRetry = viewModel::retryPage,
                        onController = { controller = it },
                        jump = state.chapterJumps,
                        onSave = { page -> savePage(context, state, page) },
                        onCopy = { page ->
                            scope.launch(Dispatchers.IO) {
                                page.stream?.let { PageActions.copyImage(it().use { s -> s.readBytes() }) }
                                context.toast(MR.strings.desktop_image_copied)
                            }
                        },
                    )
                }
            }
        }
        ReaderControls(
            state = state,
            mode = mode,
            onBack = onClose,
            onToggleBookmark = viewModel::toggleChapterBookmark,
            onOpenInWebView = chapterUrl?.let { url ->
                { EmbeddedBrowser.open(context, url, (state.source as? HttpSource)?.headers) }
            },
            onOpenInBrowser = chapterUrl?.let { url -> { context.openInBrowser(url) } },
            onPreviousChapter = { scope.launch { viewModel.loadPreviousChapter() } },
            onNextChapter = { scope.launch { viewModel.loadNextChapter() } },
            onPageIndexChange = { index -> scope.launch { controller?.goToPage(index) } },
            onOpenSettings = { showSettings = true },
            isFullscreen = window?.isFullscreen,
            onToggleFullscreen = { window?.toggleFullscreen() },
        )
        notice?.let { (text, at) ->
            LaunchedEffect(at) {
                delay(1.5.seconds)
                notice = null
            }
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.inverseSurface,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp),
            ) {
                Text(
                    text,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        if (showSettings) {
            ReaderQuickSettings(
                preferences = preferences,
                mode = mode,
                shifted = shifted,
                onModeChange = viewModel::setMangaReadingMode,
                onShiftChange = { shifted = it },
                onDismissRequest = { showSettings = false },
            )
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

private fun savePage(context: Context, state: ReaderViewModel.State, page: ReaderPage) {
    val bytes = page.stream?.invoke()?.use { it.readBytes() } ?: return
    val name = listOfNotNull(state.manga?.title, state.currentChapter?.chapter?.name, page.number.toString())
        .joinToString(" - ")
        .replace(Regex("""[\\/:*?"<>|]"""), "_")
    val file =
        FileDialogs.saveFile(context.stringResource(MR.strings.action_save), "$name.${PageActions.extensionOf(bytes)}")
            ?: return
    file.writeBytes(bytes)
    context.toast(MR.strings.picture_saved)
}

/** Mihon's dualPageView: never, always, or when the window is wider than tall. */
@Composable
private fun isDualPage(view: ReaderPreferences.DualPageView): Boolean = when (view) {
    ReaderPreferences.DualPageView.NEVER -> false
    ReaderPreferences.DualPageView.ALWAYS -> true
    ReaderPreferences.DualPageView.WIDE -> LocalWindowInfo.current.containerSize.let { it.width > it.height }
}

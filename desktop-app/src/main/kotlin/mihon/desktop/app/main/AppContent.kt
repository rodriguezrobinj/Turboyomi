package mihon.desktop.app.main

import androidx.compose.foundation.DefaultContextMenuRepresentation
import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.TabNavigator
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import eu.kanade.presentation.components.AppStateBanners
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.updates.UpdatesTab
import kotlinx.coroutines.launch
import mihon.app.di.AppGraph
import mihon.desktop.app.browser.EmbeddedBrowserConsent
import mihon.desktop.app.platform.AppMessages
import mihon.desktop.app.platform.ThemedTitleBar
import mihon.desktop.app.update.UpdateDialog
import mihon.desktop.core.cef.CefRuntime

/** What every screen needs around it: context, view model factory and root store, theme. Tests reuse it. */
@Composable
fun AppProviders(graph: AppGraph, content: @Composable () -> Unit) {
    val rootViewModels = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    CompositionLocalProvider(
        LocalContext provides graph.context,
        LocalMetroViewModelFactory provides graph.metroViewModelFactory,
        LocalViewModelStoreOwner provides rootViewModels,
    ) {
        TachiyomiTheme {
            ThemedTitleBar()
            // CMP's right-click menu has its own light style; draw it with the theme's colors
            val colors = MaterialTheme.colorScheme
            val contextMenu = remember(colors) {
                DefaultContextMenuRepresentation(
                    backgroundColor = colors.surfaceContainer,
                    textColor = colors.onSurface,
                    itemHoverColor = colors.onSurface.copy(alpha = 0.08f),
                )
            }
            CompositionLocalProvider(LocalContextMenuRepresentation provides contextMenu, content = content)
        }
    }
}

/** Everything inside the window: the crash screen if composing threw, else the navigator and the snackbar. */
@Composable
fun AppContent(graph: AppGraph, crash: Throwable?, onRestart: () -> Unit) {
    AppProviders(graph) {
        // On Android the Activity theme paints the window background and content color; here the Surface does
        Surface(Modifier.fillMaxSize()) {
            ConnectWindowBack()
            if (crash != null) {
                CrashScreen(crash, onRestart)
                return@Surface
            }
            val snackbar = remember { SnackbarHostState() }
            LaunchedEffect(Unit) {
                AppMessages.messages.collect {
                    val result = snackbar.showSnackbar(it.text, actionLabel = it.actionLabel)
                    if (result == SnackbarResult.ActionPerformed) it.onAction?.invoke()
                }
            }
            // desktop: the app's own updates (spec 6b §3 B), over whatever screen is open
            val update by graph.appUpdater.state.collectAsState()
            UpdateDialog(
                update,
                onUpdate = graph.appUpdater::update,
                onCancel = graph.appUpdater::cancel,
                onDismiss = graph.appUpdater::dismiss,
            )
            val downloadedOnly by remember { graph.basePreferences.downloadedOnly.changes() }
                .collectAsState(graph.basePreferences.downloadedOnly.get())
            val incognito by remember { graph.basePreferences.incognitoMode.changes() }
                .collectAsState(graph.basePreferences.incognitoMode.get())
            val indexing by graph.downloadCache.isInitializing.collectAsState()
            // desktop: Mihon's banners over the whole window (MainActivity)
            Column(Modifier.fillMaxSize()) {
                AppStateBanners(downloadedOnlyMode = downloadedOnly, incognitoMode = incognito, indexing = indexing)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    // The tabs' navigator sits above the screens' navigator, so it stays composed while a screen covers
                    // HomeScreen; inside HomeScreen it lost every tab's state (pager page, scroll) on the way back.
                    // Voyager's default TabDisposable would drop that state too, and the tabs live as long as the app.
                    TabNavigator(HomeScreen.tabs.first(), tabDisposable = {}) { tabNavigator ->
                        Navigator(HomeScreen) { navigator ->
                            // Shortcuts that navigate are collected here, at the root, which stays composed whatever
                            // screen is on top (Voyager only composes the top screen)
                            LaunchedEffect(navigator) {
                                launch { AppEvents.globalSearch.collect { navigator.push(GlobalSearchScreen()) } }
                                launch {
                                    AppEvents.updatesRequests.collect {
                                        navigator.popUntilRoot()
                                        tabNavigator.current = UpdatesTab
                                    }
                                }
                                AppEvents.openSettings.collect {
                                    navigator.popUntilRoot()
                                    tabNavigator.current = HomeScreen.tabs.last()
                                }
                            }
                            // Like Mihon's SettingsScreen: settings screens draw their up arrow from LocalBackPress
                            CompositionLocalProvider(LocalBackPress provides navigator::pop) {
                                DefaultNavigatorScreenTransition(navigator)
                            }
                        }
                    }
                    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
                    // The embedded browser's download dialog, over whatever screen asked for a WebView (spec 2b §3 D)
                    EmbeddedBrowserConsent(graph.cefInstaller) {
                        CefRuntime.startAsync(graph.appDirs.cef, graph.appDirs.cefCache)
                    }
                    // F1 or Settings › About (spec 8a §3 C), over whatever screen is open
                    if (ShortcutHelp.open) {
                        ShortcutHelpDialog(ShortcutHelp.readerViewer) { ShortcutHelp.open = false }
                    }
                }
            }
        }
    }
}

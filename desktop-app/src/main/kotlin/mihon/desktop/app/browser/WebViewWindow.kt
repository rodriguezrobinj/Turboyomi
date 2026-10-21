package mihon.desktop.app.browser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import mihon.app.di.AppGraph
import mihon.desktop.app.main.AppProviders
import mihon.desktop.app.startup.MIN_HEIGHT
import mihon.desktop.app.startup.MIN_WIDTH
import mihon.desktop.core.AppDirs
import mihon.desktop.core.cef.BrowserSession
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.automirroredrounded.ArrowBack
import mihon.icons.materialsymbols.automirroredrounded.ArrowForward
import mihon.icons.materialsymbols.automirroredrounded.OpenInNew
import mihon.icons.materialsymbols.rounded.Close
import mihon.icons.materialsymbols.rounded.Delete
import mihon.icons.materialsymbols.rounded.Refresh
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import xyz.nulldev.androidcompat.webkit.CefHelper
import java.awt.Dimension

/** The WebView window (spec 2b §3 E): one at a time, reused and brought to the front for the next page asked for. */
@Composable
fun EmbeddedBrowserWindow(graph: AppGraph) {
    val request = EmbeddedBrowser.window ?: return
    Window(
        onCloseRequest = EmbeddedBrowser::close,
        state = rememberWindowState(size = DpSize(1100.dp, 800.dp)),
        title = AppDirs.APP_NAME,
    ) {
        window.minimumSize = Dimension(MIN_WIDTH, MIN_HEIGHT)
        LaunchedEffect(request) { window.toFront() }
        AppProviders(graph) {
            Surface(Modifier.fillMaxSize()) { WebViewContent(request) }
        }
    }
}

@Composable
private fun WebViewContent(request: WebViewRequest) {
    val context = LocalContext.current
    // The window can open while the browser is still starting in the background
    val session by produceState<Result<BrowserSession>?>(null) {
        value = runCatching {
            val app = withContext(Dispatchers.IO) { CefHelper.waitForInit().first() }
            BrowserSession(app, request.url, request.userAgent)
        }
    }
    val current = session
    when {
        current == null -> LoadingScreen()
        current.isFailure -> EmptyScreen(message = with(context) { current.exceptionOrNull()!!.formattedMessage })
        else -> BrowserPane(current.getOrThrow(), request)
    }
}

@Composable
private fun BrowserPane(session: BrowserSession, request: WebViewRequest) {
    val context = LocalContext.current
    DisposableEffect(session) { onDispose { session.dispose() } }
    // Asked for another page while the window is open: it replaces the one shown
    var shown by remember { mutableStateOf(request) }
    LaunchedEffect(request) {
        if (request != shown) {
            shown = request
            session.load(request.url)
        }
    }
    val title by session.title.collectAsState()
    val url by session.currentUrl.collectAsState()
    val canGoBack by session.canGoBack.collectAsState()
    val canGoForward by session.canGoForward.collectAsState()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        title.ifBlank { url },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        url,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = EmbeddedBrowser::close) {
                    Icon(MaterialSymbols.Rounded.Close, contentDescription = stringResource(MR.strings.action_close))
                }
            },
            // Buttons, not a menu: a Compose popup would be drawn under the native browser below
            actions = {
                IconButton(onClick = session::back, enabled = canGoBack) {
                    Icon(
                        MaterialSymbols.AutoMirroredRounded.ArrowBack,
                        contentDescription = stringResource(MR.strings.action_webview_back),
                    )
                }
                IconButton(onClick = session::forward, enabled = canGoForward) {
                    Icon(
                        MaterialSymbols.AutoMirroredRounded.ArrowForward,
                        contentDescription = stringResource(MR.strings.action_webview_forward),
                    )
                }
                IconButton(onClick = session::reload) {
                    Icon(
                        MaterialSymbols.Rounded.Refresh,
                        contentDescription = stringResource(MR.strings.action_webview_refresh),
                    )
                }
                IconButton(
                    onClick = {
                        session.clearSiteCookies()
                        context.toast(MR.strings.cookies_cleared)
                    },
                ) {
                    Icon(
                        MaterialSymbols.Rounded.Delete,
                        contentDescription = stringResource(MR.strings.desktop_cef_clear_site_cookies),
                    )
                }
                IconButton(onClick = { context.openInBrowser(url) }) {
                    Icon(
                        MaterialSymbols.AutoMirroredRounded.OpenInNew,
                        contentDescription = stringResource(MR.strings.action_open_in_browser),
                    )
                }
            },
        )
        // The native browser takes all the space under the bar: nothing of Compose draws over it
        SwingPanel(factory = { session.browser.uiComponent }, modifier = Modifier.fillMaxSize())
    }
}

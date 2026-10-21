package mihon.desktop.core.cef

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import org.cef.CefApp
import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefRendering
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandlerAdapter

/**
 * The browser of the WebView window (spec 2b §3 E): CEF with its own native window, the app's cookies in and out,
 * the source's user agent, popups in this same browser. It exposes nothing to the page's JavaScript: its client is
 * created directly, without AndroidCompat's bridge. Without a download handler CEF cancels downloads, and it ignores
 * external protocols.
 */
class BrowserSession(app: CefApp, url: String, private val userAgent: String?) {
    private val client: CefClient = app.createClient()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _currentUrl = MutableStateFlow(url)
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()

    private val _canGoForward = MutableStateFlow(false)
    val canGoForward: StateFlow<Boolean> = _canGoForward.asStateFlow()

    val browser: CefBrowser

    init {
        CefCookieSync.pushAll()
        client.addDisplayHandler(
            object : CefDisplayHandlerAdapter() {
                override fun onAddressChange(browser: CefBrowser, frame: CefFrame, url: String) {
                    if (frame.isMain) _currentUrl.value = url
                }

                override fun onTitleChange(browser: CefBrowser, title: String) {
                    _title.value = title
                }
            },
        )
        client.addLoadHandler(
            object : CefLoadHandlerAdapter() {
                override fun onLoadingStateChange(
                    browser: CefBrowser,
                    isLoading: Boolean,
                    canGoBack: Boolean,
                    canGoForward: Boolean,
                ) {
                    _canGoBack.value = canGoBack
                    _canGoForward.value = canGoForward
                }

                override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                    // A login or a solved challenge reaches the app's requests without closing the window
                    if (frame.isMain) CefCookieSync.pull(frame.url)
                }
            },
        )
        client.addLifeSpanHandler(
            object : CefLifeSpanHandlerAdapter() {
                // window.open and target=_blank: in this same browser
                override fun onBeforePopup(
                    browser: CefBrowser,
                    frame: CefFrame,
                    targetUrl: String?,
                    targetFrameName: String?,
                ): Boolean {
                    targetUrl?.let(browser::loadURL)
                    return true
                }

                override fun onAfterCreated(browser: CefBrowser) {
                    val ua = userAgent
                    if (ua == null) {
                        browser.loadURL(url)
                    } else {
                        browser.devToolsClient
                            .executeDevToolsMethod(
                                "Emulation.setUserAgentOverride",
                                Json.encodeToString(mapOf("userAgent" to ua)),
                            )
                            .whenComplete { _, _ -> browser.loadURL(url) }
                    }
                }
            },
        )
        // Blank first: the page loads once the user agent is set, when the native window exists (onAfterCreated)
        browser = client.createBrowser("about:blank", CefRendering.DEFAULT, false)
    }

    fun load(url: String) = browser.loadURL(url)

    fun back() = browser.goBack()

    fun forward() = browser.goForward()

    fun reload() = browser.reload()

    /** "Borrar las cookies de este sitio": in CEF and in the app, then the page again without them. */
    fun clearSiteCookies() {
        CefCookieSync.clear(currentUrl.value)
        browser.reload()
    }

    fun dispose() {
        CefCookieSync.pullAll()
        browser.close(true)
        client.dispose()
    }
}

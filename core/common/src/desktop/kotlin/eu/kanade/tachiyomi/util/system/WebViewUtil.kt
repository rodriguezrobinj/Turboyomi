package eu.kanade.tachiyomi.util.system

import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import okhttp3.Headers
import java.util.Locale

// desktop: the part of Mihon's WebViewUtil that the interceptors use. Without androidx.webkit, setUserAgent sets the
// user agent only; AndroidCompat's WebView derives the rest

fun WebView.setDefaultSettings() {
    with(settings) {
        javaScriptEnabled = true
        domStorageEnabled = true
        useWideViewPort = true
        loadWithOverviewMode = true
        cacheMode = WebSettings.LOAD_DEFAULT

        // Handle popups properly
        setSupportMultipleWindows(true)

        // Allow zooming
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
    }

    CookieManager.getInstance().acceptThirdPartyCookies(this)
}

fun WebView.setUserAgent(userAgent: String) {
    settings.userAgentString = userAgent
}

fun Headers.toWebViewHeaders(): Map<String, String> {
    return this
        // Keeping unsafe header makes webview throw [net::ERR_INVALID_ARGUMENT]
        .filter { (name, value) ->
            isRequestHeaderSafe(name, value)
        }
        .groupBy(keySelector = { (name, _) -> name }) { (_, value) -> value }
        .mapValues { it.value.getOrNull(0).orEmpty() }
}

// Based on [IsRequestHeaderSafe] in
// https://source.chromium.org/chromium/chromium/src/+/main:services/network/public/cpp/header_util.cc
private fun isRequestHeaderSafe(_name: String, _value: String): Boolean {
    val name = _name.lowercase(Locale.ENGLISH)
    val value = _value.lowercase(Locale.ENGLISH)
    if (name in unsafeHeaderNames || name.startsWith("proxy-")) return false
    if (name == "connection" && value == "upgrade") return false
    return true
}
private val unsafeHeaderNames = listOf(
    "content-length", "host", "trailer", "te", "upgrade", "cookie2", "keep-alive", "transfer-encoding", "set-cookie",
    // Renderer is killed if it doesn't match the request initiator
    "origin",
)

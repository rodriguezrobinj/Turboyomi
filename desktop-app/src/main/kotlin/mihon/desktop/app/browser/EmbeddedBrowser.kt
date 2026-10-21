package mihon.desktop.app.browser

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.network.EmbeddedBrowserUnavailableException
import eu.kanade.tachiyomi.util.system.toast
import okhttp3.Headers
import xyz.nulldev.androidcompat.webkit.CefHelper

/** A page for the WebView window. [userAgent] is the source's, so the site sees the same browser as its requests. */
data class WebViewRequest(val url: String, val userAgent: String? = null)

/** The download dialog. [then] opens once the browser is installed; null when it's installed from the settings. */
data class ConsentRequest(val then: WebViewRequest?)

/** What the UI shows of the embedded browser (spec 2b §3 C, D): the WebView window and the download dialog. */
object EmbeddedBrowser {
    var window: WebViewRequest? by mutableStateOf(null)
        private set

    var consent: ConsentRequest? by mutableStateOf(null)
        private set

    /**
     * "Abrir en WebView": the window when the browser is there, even if it's still starting (the window waits for
     * it); the download dialog when it isn't installed; the reason when it didn't start.
     */
    fun open(context: Context, url: String, headers: Headers? = null) {
        val request = WebViewRequest(url, headers?.get("User-Agent"))
        val failure = CefHelper.cefApp.value.exceptionOrNull()
        when {
            failure == null -> window = request
            (failure as? EmbeddedBrowserUnavailableException)?.reason ==
                EmbeddedBrowserUnavailableException.Reason.NOT_INSTALLED -> consent = ConsentRequest(request)
            else -> context.toast(with(context) { failure.formattedMessage })
        }
    }

    /** From the settings: the download dialog, with no page to open after. */
    fun requestInstall() {
        consent = ConsentRequest(then = null)
    }

    /** The download finished: opens what was asked for. */
    fun installed() {
        consent?.then?.let { window = it }
        consent = null
    }

    fun dismissConsent() {
        consent = null
    }

    fun close() {
        window = null
    }
}

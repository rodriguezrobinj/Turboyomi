package eu.kanade.tachiyomi.network.interceptor

import android.content.Context
import android.webkit.WebView
import eu.kanade.tachiyomi.network.EmbeddedBrowserUnavailableException
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import eu.kanade.tachiyomi.util.system.setUserAgent
import kotlinx.coroutines.CoroutineScope
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import xyz.nulldev.androidcompat.webkit.CefHelper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Desktop copy of Mihon's WebViewInterceptor, over AndroidCompat's WebView and the embedded browser (spec 2b §3 A). */
abstract class WebViewInterceptor(
    private val context: Context,
    @Suppress("UNUSED_PARAMETER") scope: CoroutineScope,
    private val defaultUserAgentProvider: () -> String,
) : Interceptor {

    abstract fun shouldIntercept(response: Response): Boolean

    abstract fun intercept(chain: Interceptor.Chain, request: Request, response: Response): Response

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (!shouldIntercept(response)) {
            return response
        }

        if (!prepareWebView()) return response

        return intercept(chain, request, response)
    }

    /**
     * Returns whether a WebView can be used.
     *
     * desktop: when the embedded browser isn't installed or didn't start it throws instead of toasting, so the screen
     * that made the request says why and offers "Abrir en WebView", which installs it.
     */
    protected fun prepareWebView(): Boolean {
        CefHelper.cefApp.value.exceptionOrNull()?.let { failure ->
            throw failure as? EmbeddedBrowserUnavailableException
                ?: EmbeddedBrowserUnavailableException(EmbeddedBrowserUnavailableException.Reason.NOT_STARTED, failure)
        }
        return true
    }

    fun CountDownLatch.awaitFor30Seconds() {
        await(30, TimeUnit.SECONDS)
    }

    fun createWebView(request: Request): WebView {
        return WebView(context).apply {
            setDefaultSettings()
            // Avoid sending empty User-Agent, Chromium WebView will reset to default if empty
            setUserAgent(request.header("User-Agent") ?: defaultUserAgentProvider())
        }
    }
}

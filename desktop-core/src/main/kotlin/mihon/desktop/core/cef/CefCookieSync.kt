package mihon.desktop.core.cef

import mihon.desktop.core.cookies.PersistentCookieStore
import org.cef.network.CefCookieManager
import xyz.nulldev.androidcompat.webkit.KcefWebViewProvider
import java.net.CookieHandler
import java.net.CookieManager
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The app's cookies (OkHttp's store) and the embedded browser's, kept the same (spec 2b §2): into CEF before a
 * browser loads, back after every page. AndroidCompat's WebView calls it as its [KcefWebViewProvider.InitBrowserHandler].
 */
object CefCookieSync : KcefWebViewProvider.InitBrowserHandler {
    private val store: PersistentCookieStore?
        get() = (CookieHandler.getDefault() as? CookieManager)?.cookieStore as? PersistentCookieStore

    private val cef: CefCookieManager? get() = if (CefRuntime.isRunning) CefCookieManager.getGlobalManager() else null

    override fun init(provider: KcefWebViewProvider) = pushAll()

    /** Before onPageFinished: a client checking cookies there (the Cloudflare interceptor) must see the page's. */
    override fun onLoadEnd(url: String) = pull(url, wait = true)

    /** Every cookie of the app into CEF. */
    fun pushAll() {
        val cef = cef ?: return
        store?.all()?.forEach { (uri, cookie) -> cef.setCookie(cookieUrl(uri, cookie), cookie.toCefCookie()) }
    }

    /**
     * CEF's cookies for [url] into the app's store. With [wait], for a short while: CEF only calls the visitor when
     * there are cookies, so on a page without them the wait runs out instead of holding the page.
     */
    fun pull(url: String, wait: Boolean = false) {
        val cef = cef ?: return
        val store = store ?: return
        val uri = runCatching { URI(url) }.getOrNull() ?: return
        val done = CountDownLatch(1)
        val visiting = cef.visitUrlCookies(url, true) { cookie, count, total, _ ->
            store.add(uri, cookie.toHttpCookie())
            if (count + 1 >= total) done.countDown()
            true
        }
        if (visiting && wait) done.await(PULL_WAIT_MS, TimeUnit.MILLISECONDS)
    }

    /** All of CEF's cookies into the app's store, when the WebView window closes. */
    fun pullAll() {
        val cef = cef ?: return
        val store = store ?: return
        cef.visitAllCookies { cookie, _, _, _ ->
            store.add(cookie.url(), cookie.toHttpCookie())
            true
        }
    }

    /** Deletes the cookies of [url]'s site, or all of them with null, in both stores ("Borrar cookies", spec 2b §4). */
    fun clear(url: String?) {
        val store = store
        if (url == null) {
            store?.removeAll()
            cef?.deleteCookies("", "")
        } else {
            val uri = runCatching { URI(url) }.getOrNull() ?: return
            store?.get(uri)?.forEach { store.remove(uri, it) }
            cef?.deleteCookies(url, "")
        }
    }

    // ponytail: a fixed short wait, since CEF has no "no cookies" callback; enough for one page's cookies
    private const val PULL_WAIT_MS = 300L
}

package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Desktop stand-in for Mihon's CloudflareInterceptor. Extensions find interceptors in NetworkHelper.client by
 * class simple name and refuse to build their client without this one, so it must exist under this exact name.
 * ponytail: passes requests through; solving challenges needs the desktop WebView (KCEF, subproject 2)
 */
class CloudflareInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
}

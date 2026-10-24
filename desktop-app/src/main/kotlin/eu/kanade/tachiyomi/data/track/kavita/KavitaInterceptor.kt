package eu.kanade.tachiyomi.data.track.kavita

import mihon.desktop.core.DesktopBuildInfo
import okhttp3.Interceptor
import okhttp3.Response

class KavitaInterceptor(private val kavita: Kavita) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        if (kavita.authentications == null) {
            kavita.loadOAuth()
        }
        val jwtToken = kavita.authentications?.getToken(
            kavita.api.getApiFromUrl(originalRequest.url.toString()),
        )

        // Add the authorization header to the original request.
        val authRequest = originalRequest.newBuilder()
            .addHeader("Authorization", "Bearer $jwtToken")
            .header("User-Agent", "Mihon v${DesktopBuildInfo.VERSION_NAME} (${DesktopBuildInfo.APPLICATION_ID})")
            .build()

        return chain.proceed(authRequest)
    }
}

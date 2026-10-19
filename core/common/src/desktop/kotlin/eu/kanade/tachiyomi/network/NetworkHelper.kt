package eu.kanade.tachiyomi.network

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import kotlinx.coroutines.CoroutineScope
import mihon.core.metro.AppCoroutineScope
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.net.CookieHandler
import java.net.CookiePolicy
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import java.net.CookieManager as JavaCookieManager

/**
 * Desktop copy of Mihon's NetworkHelper. Same public API, which extensions link against.
 * ponytail: its CloudflareInterceptor passes through until the desktop WebView exists (subproject 2).
 */
@Inject
@SingleIn(AppScope::class)
class NetworkHelper(
    @Suppress("UNUSED_PARAMETER") @AppCoroutineScope scope: CoroutineScope,
    private val context: Context,
    private val preferences: NetworkPreferences,
) {

    init {
        // AndroidCompat's android.webkit.CookieManager stores cookies in the JVM-wide CookieHandler.
        // ponytail: in-memory cookies; a persistent store comes with logins and Cloudflare (subproject 2)
        if (CookieHandler.getDefault() !is JavaCookieManager) {
            CookieHandler.setDefault(JavaCookieManager(null, CookiePolicy.ACCEPT_ALL))
        }
    }

    val cookieJar = AndroidCookieJar()

    val client: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(30.seconds)
            .readTimeout(30.seconds)
            .callTimeout(2.minutes)
            .cache(
                Cache(
                    directory = File(context.cacheDir, "network_cache"),
                    maxSize = 5L * 1024 * 1024, // 5 MiB
                ),
            )
            .addInterceptor(UncaughtExceptionInterceptor())
            .addInterceptor(UserAgentInterceptor(::defaultUserAgentProvider))

        if (preferences.verboseLogging.get()) {
            builder.addNetworkInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.HEADERS },
            )
        }

        when (preferences.dohProvider.get()) {
            PREF_DOH_CLOUDFLARE -> builder.dohCloudflare()
            PREF_DOH_GOOGLE -> builder.dohGoogle()
            PREF_DOH_ADGUARD -> builder.dohAdGuard()
            PREF_DOH_QUAD9 -> builder.dohQuad9()
            PREF_DOH_ALIDNS -> builder.dohAliDNS()
            PREF_DOH_DNSPOD -> builder.dohDNSPod()
            PREF_DOH_360 -> builder.doh360()
            PREF_DOH_QUAD101 -> builder.dohQuad101()
            PREF_DOH_MULLVAD -> builder.dohMullvad()
            PREF_DOH_CONTROLD -> builder.dohControlD()
            PREF_DOH_NJALLA -> builder.dohNajalla()
            PREF_DOH_SHECAN -> builder.dohShecan()
            else -> builder
        }
            // Same position as Mihon: after the default interceptors, applied to the regular client
            .addInterceptor(CloudflareInterceptor())
            .build()
    }

    /**
     * @deprecated Since extension-lib 1.5
     */
    @Deprecated("The regular client handles Cloudflare by default")
    @Suppress("UNUSED")
    val cloudflareClient: OkHttpClient get() = client

    fun defaultUserAgentProvider() = preferences.defaultUserAgent.get().trim()
}

package eu.kanade.tachiyomi.network

import java.io.IOException

/**
 * The embedded browser that WebViews and Cloudflare challenges need isn't there or didn't start (spec 2b §3 A).
 * An IOException, so OkHttp hands it on to the screen that made the request.
 */
class EmbeddedBrowserUnavailableException(
    val reason: Reason,
    cause: Throwable? = null,
) : IOException(reason.description, cause) {
    enum class Reason(val description: String) {
        NOT_INSTALLED("The embedded browser isn't installed"),
        NOT_STARTED("The embedded browser didn't start"),
    }
}

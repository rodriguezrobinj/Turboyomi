package mihon.desktop.app.track

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * The slice of android.net.Uri the trackers use to build http(s) urls (buildUpon, appendPath, appendQueryParameter),
 * over OkHttp's HttpUrl. Ported tracker code imports this instead of android.net.Uri (plan 5b-2, task 1).
 */
class Uri private constructor(private val url: HttpUrl) {
    fun buildUpon(): Builder = Builder(url.newBuilder())

    override fun toString(): String = url.toString()

    class Builder internal constructor(private val builder: HttpUrl.Builder) {
        fun appendPath(segment: String): Builder = apply { builder.addPathSegment(segment) }

        fun appendQueryParameter(key: String, value: String?): Builder = apply { builder.addQueryParameter(key, value) }

        fun build(): Uri = Uri(builder.build())
    }

    companion object {
        fun of(url: String): Uri = Uri(url.toHttpUrl())
    }
}

fun String.toUri(): Uri = Uri.of(this)

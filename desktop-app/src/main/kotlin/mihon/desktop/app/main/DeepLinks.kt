package mihon.desktop.app.main

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import mihon.desktop.app.track.TrackLoginHandler
import java.io.File
import java.net.URI
import java.net.URLDecoder

/**
 * What Mihon gets as intents comes here as the exe's arguments (spec 11c §2): repo links, backup files, and manga
 * links or searches. The first start's and a second instance's wait in [links] until the window collects them.
 */
object DeepLinks {
    /** Mihon's add-repo schemes; the OAuth ones stay with LoginUrls. */
    val SCHEMES = setOf("tachiyomi", "mihon")

    sealed interface Link {
        data class AddRepo(val url: String) : Link
        data class RestoreBackup(val path: String) : Link
        data class Search(val query: String) : Link
    }

    private val channel = Channel<Link>(Channel.UNLIMITED)
    val links: Flow<Link> = channel.receiveAsFlow()

    fun route(args: List<String>) {
        args.mapNotNull(::parse).forEach { channel.trySend(it) }
    }

    fun parse(arg: String): Link? {
        val text = arg.trim()
        if (text.isEmpty() || text.startsWith("-")) return null
        val scheme = text.substringBefore("://", missingDelimiterValue = "").lowercase()
        if (scheme in TrackLoginHandler.SCHEMES) return null
        if (scheme in SCHEMES) {
            val uri = runCatching { URI(text) }.getOrNull() ?: return null
            // MainActivity.isAddExtensionStoreIntent
            val addsRepo = (scheme == "tachiyomi" && uri.host == "add-repo") ||
                (scheme == "mihon" && uri.host == "extension-store")
            if (!addsRepo) return null
            return queryParameter(uri, "url")?.let(Link::AddRepo)
        }
        if (text.endsWith(".tachibk", ignoreCase = true)) return Link.RestoreBackup(text).takeIf { File(text).isFile }
        return Link.Search(text)
    }

    private fun queryParameter(uri: URI, name: String): String? =
        uri.rawQuery?.split('&')
            ?.map { it.split('=', limit = 2) }
            ?.firstOrNull { it[0] == name && it.size == 2 }
            ?.let { URLDecoder.decode(it[1], Charsets.UTF_8) }
            ?.takeIf { it.isNotBlank() }
}

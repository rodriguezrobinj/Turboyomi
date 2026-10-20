// Null implementation until subproject 4; replace with the Mihon port.
package tachiyomi.source.local

import eu.kanade.tachiyomi.source.Source
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.model.Source as DomainSource

/** Mihon's local source id; nothing on desktop has it until local reading arrives. */
object LocalSource {
    const val ID = 0L
    const val HELP_URL = "https://mihon.app/docs/guides/local-source/"
}

fun Manga.isLocal(): Boolean = source == LocalSource.ID

fun Source.isLocal(): Boolean = id == LocalSource.ID

fun DomainSource.isLocal(): Boolean = id == LocalSource.ID

package mihon.desktop.app.manga

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.download.DownloadManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import mihon.desktop.core.AppDirs
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaRemoteUpdate
import tachiyomi.domain.manga.repository.MangaRepository
import kotlin.io.path.exists
import kotlin.io.path.moveTo
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * The details the user edited on a library manga (spec 13 §4). Saved into the manga's own columns, so the whole app and
 * the backups show them with Mihon's schema, and kept in `manga-edits.json` so a refresh from the source can't undo
 * them. Keyed by source and url, which a restored backup keeps.
 */
@Inject
@SingleIn(AppScope::class)
class MangaEdits(
    appDirs: AppDirs,
    private val mangaRepository: MangaRepository,
    private val downloadManager: DownloadManager,
) {
    @Serializable
    data class Edit(
        val title: String,
        val author: String,
        val artist: String,
        val description: String,
        val genre: List<String>,
        val status: Long,
        /** The title before the first edit, which reset puts back: a refresh leaves a library manga's title alone. */
        val sourceTitle: String? = null,
    )

    private val file = appDirs.data.resolve("manga-edits.json")
    private val edits: MutableMap<String, Edit> by lazy { load() }

    fun get(manga: Manga): Edit? = synchronized(this) { edits[key(manga)] }

    suspend fun save(manga: Manga, changes: Edit) {
        val edit = synchronized(this) {
            changes.copy(sourceTitle = edits[key(manga)]?.sourceTitle ?: manga.title).also {
                edits[key(manga)] = it
                write()
            }
        }
        mangaRepository.updateRemote(titleUpdate(manga, edit.title).withEdit(edit))
        // The downloads folder is named after the title, as when a refresh renames it
        if (edit.title != manga.title) downloadManager.renameManga(manga, edit.title)
    }

    /** Drops the edit and puts the source's title back; a refresh then brings the other details back. */
    suspend fun clear(manga: Manga) {
        val edit = synchronized(this) { edits.remove(key(manga))?.also { write() } } ?: return
        val title = edit.sourceTitle?.takeIf { it != manga.title } ?: return
        mangaRepository.updateRemote(titleUpdate(manga, title))
        downloadManager.renameManga(manga, title)
    }

    /** The manga as it is, but [title]: null details and cover are left alone. */
    private fun titleUpdate(manga: Manga, title: String) = MangaRemoteUpdate(
        id = manga.id,
        title = title,
        author = null,
        artist = null,
        description = null,
        genre = null,
        status = manga.status,
        thumbnailUrl = null,
        updateStrategy = manga.updateStrategy,
        memo = manga.memo,
        initialized = manga.initialized,
        coverLastModified = null,
    )

    private fun load(): MutableMap<String, Edit> = try {
        if (file.exists()) json.decodeFromString<Map<String, Edit>>(file.readText()).toMutableMap() else mutableMapOf()
    } catch (e: Exception) {
        logcat(LogPriority.ERROR, e) { "Couldn't read the manga edits" }
        mutableMapOf()
    }

    // Through a temporary file, so a crash mid-write leaves the last good one
    private fun write() {
        val part = file.resolveSibling("${file.fileName}.part")
        part.writeText(json.encodeToString(edits.toMap()))
        part.moveTo(file, overwrite = true)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun key(manga: Manga) = "${manga.source}:${manga.url}"
    }
}

/**
 * The source's details with the user's edit on top. The title stays as it is in the database, which already holds the
 * edited one: a null title is left alone, and no downloads folder gets renamed.
 */
fun MangaRemoteUpdate.withEdit(edit: MangaEdits.Edit?): MangaRemoteUpdate = if (edit == null) {
    this
} else {
    copy(
        title = if (title == edit.title) title else null,
        author = edit.author,
        artist = edit.artist,
        description = edit.description,
        genre = edit.genre,
        status = edit.status,
    )
}

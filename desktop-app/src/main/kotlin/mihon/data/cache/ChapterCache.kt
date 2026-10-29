package mihon.data.cache

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.DownloadStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import logcat.LogPriority
import mihon.core.common.cache.TtlDiskCache
import mihon.core.metro.AppCoroutineScope
import mihon.desktop.core.AppDirs
import okhttp3.Response
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * The page lists and images of chapters read online.
 *
 * Page lists expire soon, as sources sign or rotate their image URLs. Images last longer.
 */
@Inject
@SingleIn(AppScope::class)
class ChapterCache(
    private val appDirs: AppDirs,
    private val json: Json,
    @AppCoroutineScope scope: CoroutineScope,
) {

    // desktop: %LOCALAPPDATA%\<App>\cache\chapter_cache (spec 2a §4)
    private val root = appDirs.cache.resolve("chapter_cache").toFile()

    // Out of the cache directory, so clearing that leaves the indexes
    private val indexes = appDirs.local.resolve("chapter_cache").toFile()

    private val pageLists = TtlDiskCache(File(root, "pages"), File(indexes, "pages.index"), 1.hours, scope)

    private val images = TtlDiskCache(File(root, "images"), File(indexes, "images.index"), 1.days, scope)

    init {
        scope.launch(Dispatchers.IO) {
            // desktop: the DiskLruCache folder from before the TTL cache
            appDirs.cache.resolve("chapters").toFile().deleteRecursively()
            pageLists.warmUp()
            images.warmUp()
        }
    }

    // desktop: Android's Formatter.formatFileSize needs a Context
    suspend fun readableSize(): String = "%.1f MB".format((pageLists.totalSize() + images.totalSize()) / 1_000_000.0)

    suspend fun getPageList(chapter: Chapter): List<Page>? {
        val input = pageLists.open(chapter.key) ?: return null
        return try {
            json.decodeFromString(withContext(Dispatchers.IO) { input.bufferedReader().use { it.readText() } })
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logcat(LogPriority.WARN, e) { "Failed to read page list from cache" }
            pageLists.remove(chapter.key)
            null
        }
    }

    suspend fun putPageList(chapter: Chapter, pages: List<Page>) {
        try {
            val value = json.encodeToString(pages).toByteArray()
            pageLists.put(chapter.key) { it.write(value) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logcat(LogPriority.WARN, e) { "Failed to put page list to cache" }
        }
    }

    suspend fun isImageInCache(imageUrl: String): Boolean = imageUrl in images

    /**
     * The image's file, or null when it isn't cached.
     */
    suspend fun getImageFile(imageUrl: String): File? = images.get(imageUrl)

    /**
     * Opens the image, or returns null when it isn't cached.
     */
    suspend fun openImage(imageUrl: String): InputStream? = images.open(imageUrl)

    suspend fun removeImage(imageUrl: String) {
        images.remove(imageUrl)
    }

    /**
     * Stores the image [response] holds, and closes it.
     *
     * @param live also receives the bytes as they arrive.
     */
    @Throws(IOException::class)
    suspend fun putImage(imageUrl: String, response: Response, live: DownloadStream? = null): File {
        response.body.use { body ->
            live?.reserve(body.contentLength())
            return images.put(imageUrl) { out ->
                body.source().use { source ->
                    val buffer = ByteArray(1 shl 16)
                    while (true) {
                        val n = source.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        live?.append(buffer, 0, n)
                    }
                }
            }
        }
    }

    /**
     * Removes everything, returning how many entries there were.
     */
    suspend fun clear(): Int = pageLists.clear() + images.clear()

    private val Chapter.key: String
        get() = "$mangaId$url"
}

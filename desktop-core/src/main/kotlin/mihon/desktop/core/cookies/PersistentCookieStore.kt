package mihon.desktop.core.cookies

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.net.CookieManager
import java.net.CookieStore
import java.net.HttpCookie
import java.net.URI
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

/**
 * The app's cookies, kept across restarts in [file] (spec 2b §2): logins and Cloudflare clearances survive closing
 * the app. Lookups are the JDK's in-memory store; every change is written, encrypted with [protect], shortly after.
 */
class PersistentCookieStore(
    private val file: Path,
    private val protect: (ByteArray) -> ByteArray = Dpapi::protect,
    private val unprotect: (ByteArray) -> ByteArray = Dpapi::unprotect,
    private val now: () -> Long = System::currentTimeMillis,
) : CookieStore {
    private val memory: CookieStore = CookieManager().cookieStore
    private val entries = ConcurrentHashMap<Key, Entry>()
    private val writer = Executors.newSingleThreadScheduledExecutor {
        Thread(it, "cookie-store").apply { isDaemon = true }
    }
    private val saveScheduled = AtomicBoolean(false)

    init {
        load()
    }

    override fun add(uri: URI?, cookie: HttpCookie) {
        memory.add(uri, cookie)
        // A max age of 0 deletes it, as in the JDK's store
        if (cookie.maxAge ==
            0L
        ) {
            entries.remove(Key(cookie))
        } else {
            entries[Key(cookie)] = Entry(uri, cookie, expiresAt(cookie))
        }
        scheduleSave()
    }

    override fun get(uri: URI): List<HttpCookie> = memory.get(uri)

    override fun getCookies(): List<HttpCookie> = memory.cookies

    override fun getURIs(): List<URI> = memory.getURIs()

    override fun remove(uri: URI?, cookie: HttpCookie): Boolean = memory.remove(uri, cookie).also {
        entries.remove(Key(cookie))
        scheduleSave()
    }

    override fun removeAll(): Boolean = memory.removeAll().also {
        entries.clear()
        scheduleSave()
    }

    /** Every unexpired cookie with the address it came from, to copy them into the embedded browser. */
    fun all(): List<Pair<URI?, HttpCookie>> = live().map { it.uri to it.cookie }

    /** Writes pending changes now: on exit, and in tests. */
    fun flush() {
        saveScheduled.set(false)
        save()
    }

    private fun live() = entries.values.filter { it.expiresAt == null || it.expiresAt > now() }

    private fun expiresAt(cookie: HttpCookie): Long? = cookie.maxAge.takeIf { it > 0 }?.let { now() + it * 1000 }

    // Many changes in a row (a page setting a dozen cookies) are one write
    private fun scheduleSave() {
        if (saveScheduled.compareAndSet(false, true)) {
            writer.schedule({
                if (saveScheduled.compareAndSet(true, false)) save()
            }, SAVE_DELAY_MS, TimeUnit.MILLISECONDS)
        }
    }

    @Synchronized
    private fun save() {
        try {
            val bytes = protect(Json.encodeToString(live().map { it.toStored() }).toByteArray())
            file.parent.createDirectories()
            val temp = file.resolveSibling("${file.name}.tmp")
            temp.writeBytes(bytes)
            temp.moveTo(file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Couldn't save the cookies" }
        }
    }

    private fun load() {
        if (!file.exists()) return
        try {
            Json.decodeFromString<List<Stored>>(String(unprotect(file.readBytes()))).forEach { stored ->
                if (stored.expiresAt != null && stored.expiresAt <= now()) return@forEach
                val uri = stored.uri?.let(::URI)
                val cookie = stored.toCookie()
                memory.add(uri, cookie)
                entries[Key(cookie)] = Entry(uri, cookie, stored.expiresAt)
            }
        } catch (e: Exception) {
            // Another Windows user's file, or a damaged one: no cookies is better than no app
            logcat(LogPriority.ERROR, e) { "Couldn't read the saved cookies; starting without them" }
        }
    }

    private data class Key(val name: String, val domain: String?, val path: String?) {
        constructor(cookie: HttpCookie) : this(cookie.name, cookie.domain?.lowercase(), cookie.path)
    }

    private class Entry(val uri: URI?, val cookie: HttpCookie, val expiresAt: Long?)

    @Serializable
    private data class Stored(
        val uri: String?,
        val name: String,
        val value: String,
        val domain: String?,
        val path: String?,
        val expiresAt: Long?,
        val secure: Boolean,
        val httpOnly: Boolean,
        val version: Int,
    )

    private fun Entry.toStored() = Stored(
        uri = uri?.toString(),
        name = cookie.name,
        value = cookie.value,
        domain = cookie.domain,
        path = cookie.path,
        expiresAt = expiresAt,
        secure = cookie.secure,
        httpOnly = cookie.isHttpOnly,
        version = cookie.version,
    )

    private fun Stored.toCookie() = HttpCookie(name, value).also {
        it.domain = domain
        it.path = path
        it.secure = secure
        it.isHttpOnly = httpOnly
        it.version = version
        it.maxAge = expiresAt?.let { at -> (at - now()) / 1000 } ?: -1
    }

    private companion object {
        const val SAVE_DELAY_MS = 500L
    }
}

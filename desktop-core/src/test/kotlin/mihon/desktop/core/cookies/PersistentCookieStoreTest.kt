package mihon.desktop.core.cookies

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.net.HttpCookie
import java.net.URI
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

class PersistentCookieStoreTest {
    private val file = createTempDirectory("cookies José").resolve("cookies.bin")
    private val site = URI("https://example.com/")

    private fun cookie(name: String, value: String = "valor", maxAge: Long = 3600) =
        HttpCookie(name, value).apply {
            path = "/"
            this.maxAge = maxAge
        }

    @Test
    fun `cookies survive to a new store on the same file`() {
        PersistentCookieStore(file).apply {
            add(site, cookie("sesion"))
            // A session cookie (no expiry) also keeps a login across restarts
            add(site, cookie("login", maxAge = -1))
            flush()
        }

        val names = PersistentCookieStore(file).get(URI("https://example.com/manga/1")).map { it.name }

        names shouldContain "sesion"
        names shouldContain "login"
    }

    @Test
    fun `the file doesn't hold the cookie's value in clear`() {
        PersistentCookieStore(file).apply {
            add(site, cookie("sesion", value = "secreto-de-José"))
            flush()
        }

        String(file.readBytes(), Charsets.ISO_8859_1) shouldNotContain "secreto"
    }

    @Test
    fun `expired cookies are dropped when the file is read`() {
        var now = 1_000_000L
        PersistentCookieStore(file, now = { now }).apply {
            add(site, cookie("corta", maxAge = 10))
            flush()
        }

        now += 20_000
        PersistentCookieStore(file, now = { now }).cookies.shouldBeEmpty()
    }

    @Test
    fun `an unreadable file starts with no cookies instead of failing`() {
        file.parent.toFile().mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3, 4))

        val store = PersistentCookieStore(file)
        store.cookies.shouldBeEmpty()

        // And it's replaced with a good one on the next change
        store.add(site, cookie("nueva"))
        store.flush()
        PersistentCookieStore(file).cookies.map { it.name } shouldBe listOf("nueva")
    }

    @Test
    fun `removing all cookies empties the file too`() {
        PersistentCookieStore(file).apply {
            add(site, cookie("sesion"))
            flush()
            removeAll()
            flush()
        }

        PersistentCookieStore(file).cookies.shouldBeEmpty()
    }

    @Test
    fun `all lists each cookie with the address it came from`() {
        val store = PersistentCookieStore(file)
        store.add(site, cookie("sesion"))

        store.all().map { (uri, cookie) -> uri to cookie.name } shouldBe listOf(site to "sesion")
    }
}

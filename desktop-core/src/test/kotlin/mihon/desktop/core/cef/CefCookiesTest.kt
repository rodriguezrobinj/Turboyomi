package mihon.desktop.core.cef

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.net.HttpCookie
import java.net.URI
import java.util.Date

class CefCookiesTest {
    private val now = Date(1_700_000_000_000)

    @Test
    fun `a domain cookie goes to CEF and back unchanged`() {
        val cookie = HttpCookie("sesion", "José").apply {
            domain = ".example.com"
            path = "/manga"
            secure = true
            isHttpOnly = true
            maxAge = 3600
        }

        val cef = cookie.toCefCookie(now)
        cef.domain shouldBe ".example.com"
        cef.hasExpires shouldBe true
        cef.expires shouldBe Date(now.time + 3_600_000)

        val back = cef.toHttpCookie(now)
        back.name shouldBe "sesion"
        back.value shouldBe "José"
        back.domain shouldBe ".example.com"
        back.path shouldBe "/manga"
        back.secure shouldBe true
        back.isHttpOnly shouldBe true
        back.maxAge shouldBe 3600
    }

    @Test
    fun `a host cookie with no expiry stays a host session cookie`() {
        val cef = HttpCookie("login", "si").apply { path = "/" }.toCefCookie(now)

        cef.domain shouldBe ""
        cef.hasExpires shouldBe false
        cef.toHttpCookie(now).maxAge shouldBe -1
    }

    @Test
    fun `the address CEF sets a cookie for comes from its domain, or the page it came from`() {
        val secure = HttpCookie("a", "1").apply {
            domain = ".example.com"
            secure = true
        }
        val host = HttpCookie("b", "2").apply { path = "/m" }

        cookieUrl(URI("http://example.com/x"), secure) shouldBe "https://example.com/"
        cookieUrl(URI("http://example.com/x"), host) shouldBe "http://example.com/m"
    }
}

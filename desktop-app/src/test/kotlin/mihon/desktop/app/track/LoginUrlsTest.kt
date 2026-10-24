package mihon.desktop.app.track

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class LoginUrlsTest {
    @Test
    fun `only the login redirects among the arguments go to the handler`() {
        val handled = mutableListOf<String>()

        LoginUrls.route(
            listOf("--focus", "turboyomi://anilist-auth#access_token=t", "app.turboyomi://comick-auth?code=c", "x"),
        ) { handled += it }

        handled shouldBe listOf("turboyomi://anilist-auth#access_token=t", "app.turboyomi://comick-auth?code=c")
    }
}

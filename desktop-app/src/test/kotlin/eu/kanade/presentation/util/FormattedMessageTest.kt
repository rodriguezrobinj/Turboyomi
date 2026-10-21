package eu.kanade.presentation.util

import eu.kanade.tachiyomi.network.EmbeddedBrowserUnavailableException
import eu.kanade.tachiyomi.network.EmbeddedBrowserUnavailableException.Reason
import eu.kanade.tachiyomi.network.HttpException
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR
import java.io.IOException

class FormattedMessageTest {
    private val context = startTestApp().context

    private fun Throwable.message() = with(context) { formattedMessage }

    @Test
    fun `a missing browser says how to install it, even wrapped by an extension`() {
        val missing = EmbeddedBrowserUnavailableException(Reason.NOT_INSTALLED)

        missing.message() shouldBe str(MR.strings.desktop_cef_not_installed)
        IOException("la extensión falló", missing).message() shouldBe str(MR.strings.desktop_cef_not_installed)
    }

    @Test
    fun `a browser that didn't start says why`() {
        EmbeddedBrowserUnavailableException(Reason.NOT_STARTED, UnsatisfiedLinkError("falta libcef.dll"))
            .message() shouldContain "falta libcef.dll"
    }

    @Test
    fun `a failed Cloudflare challenge shows Mihon's message, and a 403 is just a 403`() {
        IOException(str(MR.strings.information_cloudflare_bypass_failure)).message() shouldBe
            str(MR.strings.information_cloudflare_bypass_failure)
        HttpException(403).message() shouldBe str(MR.strings.exception_http, 403)
    }
}

package mihon.desktop.app.security

import eu.kanade.tachiyomi.core.security.SecurityPreferences.SecureScreenMode
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.testing.TestLibrary
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.tray.newChaptersNotice
import org.junit.jupiter.api.Test

class SecureScreenTest {
    @Test
    fun `the window hides from captures always, or in incognito mode when asked`() {
        SecureScreen.isSecure(SecureScreenMode.ALWAYS, incognito = false) shouldBe true
        SecureScreen.isSecure(SecureScreenMode.INCOGNITO, incognito = true) shouldBe true
        SecureScreen.isSecure(SecureScreenMode.INCOGNITO, incognito = false) shouldBe false
        SecureScreen.isSecure(SecureScreenMode.NEVER, incognito = true) shouldBe false
    }

    @Test
    fun `hiding notification content leaves the titles out of the new chapters notice`() {
        val graph = startTestApp()
        val (manga, chapters) = runBlocking { TestLibrary.httpManga(graph) }
        val updates = listOf(manga to chapters)

        newChaptersNotice(graph.context, updates).message shouldContain manga.title
        newChaptersNotice(graph.context, updates, hideContent = true).message shouldNotContain manga.title
    }
}

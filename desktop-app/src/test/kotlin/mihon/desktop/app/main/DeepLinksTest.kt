package mihon.desktop.app.main

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import mihon.desktop.app.main.DeepLinks.Link
import org.junit.jupiter.api.Test
import kotlin.io.path.createTempFile

class DeepLinksTest {
    private val repo = "https://raw.githubusercontent.com/someone/extensions/repo/index.min.json"

    @Test
    fun `Mihon's add-repo links open the store with the repo, others are ignored`() {
        DeepLinks.parse("tachiyomi://add-repo?url=$repo") shouldBe Link.AddRepo(repo)
        DeepLinks.parse("mihon://extension-store?url=${java.net.URLEncoder.encode(repo, "UTF-8")}") shouldBe
            Link.AddRepo(repo)
        DeepLinks.parse("tachiyomi://add-repo").shouldBeNull()
        DeepLinks.parse("tachiyomi://add-repo?url=").shouldBeNull()
        DeepLinks.parse("mihon://something-else?url=$repo").shouldBeNull()
    }

    @Test
    fun `an existing backup file opens its restore, a missing one nothing`() {
        val backup = createTempFile("Copia José", ".tachibk").toString()
        DeepLinks.parse(backup) shouldBe Link.RestoreBackup(backup)
        DeepLinks.parse("C:\\no-such-folder\\backup.tachibk").shouldBeNull()
    }

    @Test
    fun `anything else is a manga link or a search, but not options or OAuth redirects`() {
        DeepLinks.parse("https://example.org/manga/123") shouldBe Link.Search("https://example.org/manga/123")
        DeepLinks.parse(" one piece ") shouldBe Link.Search("one piece")
        DeepLinks.parse("--smoke").shouldBeNull()
        DeepLinks.parse("turboyomi://anilist-auth?code=x").shouldBeNull()
        DeepLinks.parse("").shouldBeNull()
    }
}

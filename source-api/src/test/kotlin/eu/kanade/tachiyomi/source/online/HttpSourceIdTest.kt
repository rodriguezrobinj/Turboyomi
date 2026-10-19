package eu.kanade.tachiyomi.source.online

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class HttpSourceIdTest {
    private class MangaDexLike : HttpSource() {
        override val name = "MangaDex"
        override val lang = "en"
        override val baseUrl = "https://example.org"
        override val supportsLatest = false
    }

    @Test
    fun `source ids match the ones Mihon generates`() {
        // Same id Mihon on Android gives MangaDex (en); backups and libraries depend on it.
        MangaDexLike().id shouldBe 2499283573021220255L
    }
}

package mihon.desktop.harness

import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.MangasPage
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mihon.domain.extension.model.ContentWarning
import mihon.domain.extension.model.ExtensionStore
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

class ProbeTest {
    @Test
    fun `a hanging source times out instead of blocking the run`(): Unit = runBlocking {
        val source = mockk<Source> {
            coEvery { getPopularManga(1) } coAnswers
                {
                    delay(10_000)
                    MangasPage(emptyList(), false)
                }
        }
        probe(source, 100.milliseconds).shouldBeInstanceOf<ProbeTimeout>()
    }

    @Test
    fun `a source blocking its thread times out too`(): Unit = runBlocking {
        // Rx-based extensions run OkHttp's execute() on the calling thread; a stall must not hold the harness
        val source = mockk<Source> {
            coEvery { getPopularManga(1) } answers
                {
                    Thread.sleep(10_000)
                    MangasPage(emptyList(), false)
                }
        }
        val (result, elapsed) = measureTimedValue { probe(source, 200.milliseconds) }
        result.shouldBeInstanceOf<ProbeTimeout>()
        // Well under the 10 s the source blocks for; generous because the desktop UI tests load the CPU in parallel
        elapsed shouldBeLessThan 9.seconds
    }

    @Test
    fun `a working source passes`(): Unit = runBlocking {
        val source = mockk<Source> { coEvery { getPopularManga(1) } returns MangasPage(emptyList(), false) }
        probe(source, 1_000.milliseconds) shouldBe null
    }

    @Test
    fun `selection prefers the packages file then english and multi-language by package`() {
        val store = ExtensionStore("u", "n", "b", "k", ExtensionStore.Contact("w", null), false, null)
        fun ext(
            pkg: String,
            lang: String,
        ) = Extension.Available(pkg, pkg, "1.4.1", 1, 1.4, lang, ContentWarning.SAFE, emptyList(), "", "", store)
        val all = listOf(ext("c", "en"), ext("a", "all"), ext("b", "ja"), ext("d", "en"))

        selectPackages(all, packages = null, limit = 2).map { it.pkgName } shouldBe listOf("a", "c")
        selectPackages(all, packages = listOf("b", "zzz"), limit = 2).map { it.pkgName } shouldBe listOf("b")
    }
}

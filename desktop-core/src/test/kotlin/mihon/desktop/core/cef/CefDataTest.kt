package mihon.desktop.core.cef

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.writeText

class CefDataTest {
    private val root = createTempDirectory("cef José")
    private val dir = root.resolve("cef").also {
        it.resolve("Cache").createDirectories().resolve("data").writeText("x")
    }
    private val marker = root.resolve("cef.clear")

    @Test
    fun `with the browser stopped, clearing deletes the profile now`() {
        CefData(dir).clear(running = false) shouldBe true
        dir.exists() shouldBe false
    }

    @Test
    fun `with the browser running, the profile goes at the next start`() {
        val data = CefData(dir)
        data.clear(running = true) shouldBe false
        dir.exists() shouldBe true
        marker.exists() shouldBe true

        data.applyPending()
        dir.exists() shouldBe false
        marker.exists() shouldBe false
    }
}

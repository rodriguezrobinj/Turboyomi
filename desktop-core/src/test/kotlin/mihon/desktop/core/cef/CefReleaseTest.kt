package mihon.desktop.core.cef

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

class CefReleaseTest {
    @Test
    fun `the native build is the one of the jcef library the app compiles against`() {
        val catalog = File("../gradle/libs.versions.toml").readText()
        val jcef = Regex("""^jcef = "([^"]+)"""", RegexOption.MULTILINE).find(catalog)!!.groupValues[1]

        CefRelease.JCEF_VERSION shouldBe jcef
    }
}

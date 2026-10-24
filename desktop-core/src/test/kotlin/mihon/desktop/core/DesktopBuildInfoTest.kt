package mihon.desktop.core

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DesktopBuildInfoTest {
    private fun git(vararg args: String) =
        ProcessBuilder("git", *args).redirectErrorStream(true).start().inputStream.bufferedReader().readText().trim()

    @Test
    fun `the version comes from a v tag, 0_1_0 without one, and the commit is the checked out one`() {
        val tag = git("describe", "--tags", "--match", "v*", "--abbrev=0")
        val expected = Regex("""^v(\d+\.\d+\.\d+)$""").find(tag)?.groupValues?.get(1) ?: "0.1.0"

        DesktopBuildInfo.VERSION_NAME shouldBe expected
        DesktopBuildInfo.COMMIT_SHA shouldBe git("rev-parse", "--short", "HEAD")
        DesktopBuildInfo.APPLICATION_ID shouldBe "app.turboyomi"
    }
}

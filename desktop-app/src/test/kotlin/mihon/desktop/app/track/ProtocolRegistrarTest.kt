package mihon.desktop.app.track

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ProtocolRegistrarTest {
    @Test
    fun `the command quotes paths with spaces and accents and passes the url as %1`() {
        ProtocolRegistrar.command("C:\\Users\\José Pérez\\MangaReader\\MangaReader.exe", emptyList()) shouldBe
            "\"C:\\Users\\José Pérez\\MangaReader\\MangaReader.exe\" \"%1\""
        ProtocolRegistrar.command("C:\\jdk\\bin\\java.exe", listOf("-cp", "C:\\a b\\x.jar", "MainKt")) shouldBe
            "\"C:\\jdk\\bin\\java.exe\" -cp \"C:\\a b\\x.jar\" MainKt \"%1\""
    }

    @Test
    fun `both schemes are registered as url protocols under the given root`() {
        val root = "Software\\MangaReaderTest-${System.nanoTime()}"
        try {
            ProtocolRegistrar.register(root) shouldBe true

            listOf("mangareader", "app.mangareader").forEach { scheme ->
                val key = "$root\\$scheme"
                Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, key, "URL Protocol") shouldBe true
                Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, "$key\\shell\\open\\command", "")
                    .endsWith("\"%1\"") shouldBe true
            }
        } finally {
            // JNA deletes one key at a time, children first
            listOf("mangareader", "app.mangareader").forEach { scheme ->
                listOf("shell\\open\\command", "shell\\open", "shell", "").forEach { sub ->
                    val key = listOf(root, scheme, sub).filter { it.isNotEmpty() }.joinToString("\\")
                    runCatching { Advapi32Util.registryDeleteKey(WinReg.HKEY_CURRENT_USER, key) }
                }
            }
            Advapi32Util.registryDeleteKey(WinReg.HKEY_CURRENT_USER, root)
        }
    }
}

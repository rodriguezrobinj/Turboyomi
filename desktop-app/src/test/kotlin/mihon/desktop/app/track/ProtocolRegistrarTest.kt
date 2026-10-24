package mihon.desktop.app.track

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ProtocolRegistrarTest {
    @Test
    fun `the command quotes paths with spaces and accents and passes the url as %1`() {
        ProtocolRegistrar.command("C:\\Users\\José Pérez\\Turboyomi\\Turboyomi.exe", emptyList()) shouldBe
            "\"C:\\Users\\José Pérez\\Turboyomi\\Turboyomi.exe\" \"%1\""
        ProtocolRegistrar.command("C:\\jdk\\bin\\java.exe", listOf("-cp", "C:\\a b\\x.jar", "MainKt")) shouldBe
            "\"C:\\jdk\\bin\\java.exe\" -cp \"C:\\a b\\x.jar\" MainKt \"%1\""
    }

    @Test
    fun `both schemes are registered as url protocols under the given root`() {
        val root = "Software\\TurboyomiTest-${System.nanoTime()}"
        try {
            ProtocolRegistrar.register(kotlin.io.path.createTempFile("launch", ".args").toFile(), root) shouldBe true

            listOf("turboyomi", "app.turboyomi").forEach { scheme ->
                val key = "$root\\$scheme"
                Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, key, "URL Protocol") shouldBe true
                Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, "$key\\shell\\open\\command", "")
                    .endsWith("\"%1\"") shouldBe true
            }
        } finally {
            // JNA deletes one key at a time, children first
            listOf("turboyomi", "app.turboyomi").forEach { scheme ->
                listOf("shell\\open\\command", "shell\\open", "shell", "").forEach { sub ->
                    val key = listOf(root, scheme, sub).filter { it.isNotEmpty() }.joinToString("\\")
                    runCatching { Advapi32Util.registryDeleteKey(WinReg.HKEY_CURRENT_USER, key) }
                }
            }
            Advapi32Util.registryDeleteKey(WinReg.HKEY_CURRENT_USER, root)
        }
    }

    @Test
    fun `during development the JVM runs from an argument file, the packaged exe runs as it is`() {
        val argFile = kotlin.io.path.createTempDirectory("Protocolo José").resolve("launch.args").toFile()

        val dev = ProtocolRegistrar.commandFor(
            executable = "C:\\Program Files\\Java\\jdk-23\\bin\\java.exe",
            argFile = argFile,
            jvmArgs = listOf("-Dfile.encoding=UTF-8", "--add-opens=java.base/java.lang=ALL-UNNAMED"),
            classpath = "C:\\Users\\José Pérez\\a.jar;C:\\b.jar",
            mainClass = "mihon.desktop.app.MainKt",
        )

        dev shouldBe "\"C:\\Program Files\\Java\\jdk-23\\bin\\java.exe\" \"@${argFile.path}\" \"%1\""
        // Argument files take backslashes as escapes inside quotes, so paths go with forward slashes
        argFile.readText() shouldBe
            "-Dfile.encoding=UTF-8\n--add-opens=java.base/java.lang=ALL-UNNAMED\n-cp\n" +
            "\"C:/Users/José Pérez/a.jar;C:/b.jar\"\nmihon.desktop.app.MainKt\n"
        ProtocolRegistrar.commandFor("C:\\Apps\\Turboyomi.exe", argFile, emptyList(), "", "") shouldBe
            "\"C:\\Apps\\Turboyomi.exe\" \"%1\""
    }
}

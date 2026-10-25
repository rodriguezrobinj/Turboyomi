package mihon.desktop.app.update

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Path
import java.util.Base64

class UpdateInstallerTest {
    private val localAppData = Path.of("C:\\Users\\José O'Brien\\AppData\\Local")

    private fun installer(exe: String?) = UpdateInstaller(exe = { exe?.let(Path::of) }, localAppData = { localAppData })

    @Test
    fun `only the exe in Programs Turboyomi is the installed app`() {
        installer("C:\\Users\\José O'Brien\\AppData\\Local\\Programs\\Turboyomi\\Turboyomi.exe").isInstalled() shouldBe
            true
        installer("c:\\users\\josé o'brien\\appdata\\local\\programs\\turboyomi\\TURBOYOMI.EXE").isInstalled() shouldBe
            true
        installer("D:\\Apps\\Turboyomi\\Turboyomi.exe").isInstalled() shouldBe false
        installer("C:\\Program Files\\Java\\jdk-23\\bin\\java.exe").isInstalled() shouldBe false
    }

    @Test
    fun `development is a java process, not the app's exe`() {
        installer("C:\\Program Files\\Java\\jdk-23\\bin\\java.exe").isDevelopment() shouldBe true
        installer(null).isDevelopment() shouldBe true
        installer("D:\\Apps\\Turboyomi\\Turboyomi.exe").isDevelopment() shouldBe false
    }

    @Test
    fun `the command waits for the app to exit, then runs msiexec on a path with spaces, accents and an apostrophe`() {
        val msi = File("C:\\Users\\José O'Brien\\AppData\\Local\\Turboyomi\\cache\\updates\\Turboyomi-0.2.0.msi")

        val command = installer(null).command(msi, pid = 4242)

        command.take(5) shouldBe listOf("powershell.exe", "-NoProfile", "-WindowStyle", "Hidden", "-EncodedCommand")
        String(Base64.getDecoder().decode(command[5]), Charsets.UTF_16LE) shouldBe
            "Wait-Process -Id 4242 -ErrorAction SilentlyContinue; " +
            "Start-Process -FilePath 'msiexec.exe' -ArgumentList '/i', " +
            "'\"C:\\Users\\José O''Brien\\AppData\\Local\\Turboyomi\\cache\\updates\\Turboyomi-0.2.0.msi\"', " +
            "'/passive', '/norestart'"
    }
}

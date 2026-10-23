package mihon.desktop.app.platform

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

class FileDialogsTest {
    @Test
    fun `a name typed without the default's extension gets it, so the open dialog's filter finds the file`() {
        val dir = File("C:/Copias José")

        withDefaultExtension(File(dir, "copia"), "app.mangareader_2026-10-23_10-00.tachibk") shouldBe
            File(dir, "copia.tachibk")
        withDefaultExtension(File(dir, "copia.TACHIBK"), "x.tachibk") shouldBe File(dir, "copia.TACHIBK")
        withDefaultExtension(File(dir, "lista"), "mihon_library.csv") shouldBe File(dir, "lista.csv")
        withDefaultExtension(File(dir, "lista"), "sin_extension") shouldBe File(dir, "lista")
    }
}

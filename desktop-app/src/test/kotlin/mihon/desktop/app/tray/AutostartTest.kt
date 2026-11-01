package mihon.desktop.app.tray

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.io.path.Path

class AutostartTest {
    @Test
    fun `turned on it runs the quoted exe in the tray, turned off the entry goes`() {
        val key = "Software\\TurboyomiTest-${System.nanoTime()}"
        val exe = Path("C:\\Users\\José Pérez\\AppData\\Local\\Programs\\Turboyomi\\Turboyomi.exe")
        try {
            Autostart.apply(enabled = true, exe = exe, key = key)
            Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, key, "Turboyomi") shouldBe
                "\"C:\\Users\\José Pérez\\AppData\\Local\\Programs\\Turboyomi\\Turboyomi.exe\" --tray"

            Autostart.apply(enabled = false, exe = exe, key = key)
            Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, key, "Turboyomi") shouldBe false
            // Off again with nothing to remove
            Autostart.apply(enabled = false, exe = exe, key = key)
        } finally {
            Advapi32Util.registryDeleteKey(WinReg.HKEY_CURRENT_USER, key)
        }
    }
}

package mihon.desktop.app.library

import io.kotest.matchers.shouldBe
import mihon.desktop.core.DeviceState.Internet
import org.junit.jupiter.api.Test
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_CHARGING
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_NETWORK_NOT_METERED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_ONLY_ON_WIFI

class UpdateRestrictionsTest {
    private val cable = Internet(mobile = false, metered = false)
    private val meteredWifi = Internet(mobile = false, metered = true)
    private val mobile = Internet(mobile = true, metered = true)

    private fun met(restrictions: Set<String>, internet: Internet?, acPower: Boolean? = true) =
        UpdateRestrictions.met(restrictions, { internet }, { acPower })

    @Test
    fun `without restrictions the update always runs`() {
        met(emptySet(), internet = null, acPower = false) shouldBe true
    }

    @Test
    fun `Wi-Fi means no mobile data, so a cable passes`() {
        met(setOf(DEVICE_ONLY_ON_WIFI), cable) shouldBe true
        met(setOf(DEVICE_ONLY_ON_WIFI), meteredWifi) shouldBe true
        met(setOf(DEVICE_ONLY_ON_WIFI), mobile) shouldBe false
        met(setOf(DEVICE_ONLY_ON_WIFI), internet = null) shouldBe false
    }

    @Test
    fun `a metered connection holds the update when asked`() {
        met(setOf(DEVICE_NETWORK_NOT_METERED), cable) shouldBe true
        met(setOf(DEVICE_NETWORK_NOT_METERED), meteredWifi) shouldBe false
    }

    @Test
    fun `charging holds the update only on battery`() {
        met(setOf(DEVICE_CHARGING), cable, acPower = true) shouldBe true
        met(setOf(DEVICE_CHARGING), cable, acPower = false) shouldBe false
        met(setOf(DEVICE_CHARGING), cable, acPower = null) shouldBe true
    }

    @Test
    fun `a network check that fails counts as met`() {
        UpdateRestrictions.met(setOf(DEVICE_ONLY_ON_WIFI), { error("WinRT") }, { true }) shouldBe true
    }
}

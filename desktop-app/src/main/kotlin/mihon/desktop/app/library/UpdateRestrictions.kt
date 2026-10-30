package mihon.desktop.app.library

import logcat.LogPriority
import mihon.desktop.core.DeviceState
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_CHARGING
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_NETWORK_NOT_METERED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_ONLY_ON_WIFI

/**
 * Mihon's device restrictions for the automatic library update (spec 11f §2). "Wi-Fi" means no mobile data, so a
 * cable counts too: Mihon's default asks for Wi-Fi, and a desktop on a cable would never update. A check Windows
 * can't answer counts as met, so a failure never stops the updates for good.
 */
object UpdateRestrictions {
    fun met(
        restrictions: Set<String>,
        internet: () -> DeviceState.Internet?,
        acPower: () -> Boolean?,
    ): Boolean {
        if (DEVICE_ONLY_ON_WIFI in restrictions || DEVICE_NETWORK_NOT_METERED in restrictions) {
            val connection = try {
                internet() ?: return false // offline: wait for a connection, as WorkManager does
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Couldn't read the network for the update restrictions" }
                null
            }
            if (connection != null) {
                if (DEVICE_ONLY_ON_WIFI in restrictions && connection.mobile) return false
                if (DEVICE_NETWORK_NOT_METERED in restrictions && connection.metered) return false
            }
        }
        return DEVICE_CHARGING !in restrictions || acPower() != false
    }
}

package mihon.desktop.core

import com.sun.jna.Native
import com.sun.jna.Structure
import com.sun.jna.platform.win32.Guid.GUID
import com.sun.jna.ptr.ByteByReference
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import mihon.desktop.core.winrt.WinRt

/** What Mihon's library update restrictions ask of the device (spec 11f §2). Blocking, for an IO thread. */
object DeviceState {
    /** [mobile]: mobile broadband (WWAN); [metered]: Windows' metered connection, with a fixed or variable cost. */
    data class Internet(val mobile: Boolean, val metered: Boolean)

    /** The connection Windows uses for the internet, null without one; throws when WinRT fails. */
    fun internet(): Internet? {
        WinRt.initialize()
        val statics = WinRt.activationFactory(NETWORK_INFORMATION, IID_NETWORK_INFORMATION_STATICS)
        try {
            val profile = statics.interfaceAt(STATICS_GET_INTERNET_CONNECTION_PROFILE) ?: return null
            try {
                val costType = IntByReference()
                val cost = checkNotNull(profile.interfaceAt(PROFILE_GET_CONNECTION_COST))
                try {
                    WinRt.succeeded(cost.invoke(COST_NETWORK_COST_TYPE, costType))
                } finally {
                    cost.release()
                }
                val wwan = ByteByReference()
                val profile2 = profile.queryInterface(IID_CONNECTION_PROFILE_2)
                try {
                    WinRt.succeeded(profile2.invoke(PROFILE2_IS_WWAN, wwan))
                } finally {
                    profile2.release()
                }
                return Internet(
                    mobile = wwan.value.toInt() != 0,
                    metered = costType.value == COST_FIXED || costType.value == COST_VARIABLE,
                )
            } finally {
                profile.release()
            }
        } finally {
            statics.release()
        }
    }

    /** On AC power, as a desktop without a battery always is; null when Windows can't tell. */
    fun onAcPower(): Boolean? {
        val status = SystemPowerStatus()
        if (!PowerKernel32.INSTANCE.GetSystemPowerStatus(status)) return null
        return when (status.ACLineStatus.toInt() and 0xFF) {
            0 -> false
            1 -> true
            else -> null // 255: unknown
        }
    }

    /** kernel32's SYSTEM_POWER_STATUS, which jna-platform doesn't declare. */
    @Suppress("PropertyName")
    @Structure.FieldOrder(
        "ACLineStatus",
        "BatteryFlag",
        "BatteryLifePercent",
        "SystemStatusFlag",
        "BatteryLifeTime",
        "BatteryFullLifeTime",
    )
    class SystemPowerStatus : Structure() {
        @JvmField var ACLineStatus: Byte = 0

        @JvmField var BatteryFlag: Byte = 0

        @JvmField var BatteryLifePercent: Byte = 0

        @JvmField var SystemStatusFlag: Byte = 0

        @JvmField var BatteryLifeTime: Int = 0

        @JvmField var BatteryFullLifeTime: Int = 0
    }

    @Suppress("FunctionName")
    private interface PowerKernel32 : StdCallLibrary {
        fun GetSystemPowerStatus(status: SystemPowerStatus): Boolean

        companion object {
            val INSTANCE: PowerKernel32 =
                Native.load("kernel32", PowerKernel32::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }
    }

    private const val NETWORK_INFORMATION = "Windows.Networking.Connectivity.NetworkInformation"

    // windows.networking.connectivity.idl in the Windows SDK 10.0.26100
    private val IID_NETWORK_INFORMATION_STATICS = GUID.fromString("{5074F851-950D-4165-9C15-365619481EEA}")
    private val IID_CONNECTION_PROFILE_2 = GUID.fromString("{E2045145-4C9F-400C-9150-7EC7D6E2888A}")

    // vtable slots after IInspectable's six
    private const val STATICS_GET_INTERNET_CONNECTION_PROFILE = 7
    private const val PROFILE_GET_CONNECTION_COST = 9
    private const val COST_NETWORK_COST_TYPE = 6
    private const val PROFILE2_IS_WWAN = 6

    // NetworkCostType
    private const val COST_FIXED = 2
    private const val COST_VARIABLE = 3
}

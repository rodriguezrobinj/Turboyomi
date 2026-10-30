package mihon.desktop.core.security

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Guid.GUID
import com.sun.jna.ptr.PointerByReference
import logcat.LogPriority
import mihon.desktop.core.winrt.WinRt
import tachiyomi.core.common.util.system.logcat

/**
 * Windows Hello through WinRT's UserConsentVerifier (spec 11b §2): the PIN, fingerprint or face dialog of the
 * current Windows user. Blocking calls, for an IO thread; any COM failure counts as unavailable or not verified.
 */
object WindowsHello {
    fun isAvailable(): Boolean = call("check availability") { availability() == AVAILABLE }

    /** UserConsentVerifierAvailability (0 available, 1-4 why not); throws when WinRT fails. */
    internal fun availability(): Int {
        WinRt.initialize()
        val statics = WinRt.activationFactory(CLASS_NAME, IID_STATICS)
        try {
            val operation = PointerByReference()
            WinRt.succeeded(statics.invoke(STATICS_CHECK_AVAILABILITY, operation)) // CheckAvailabilityAsync
            return WinRt.asyncIntResult(WinRt.ComObject(operation.value))
        } finally {
            statics.release()
        }
    }

    /** True only when the user verified; [hwnd] owns the dialog, so it opens in front of the window. */
    fun verify(hwnd: Pointer, message: String): Boolean = call("verify") {
        WinRt.initialize()
        val interop = WinRt.activationFactory(CLASS_NAME, IID_INTEROP)
        val text = WinRt.hstring(message)
        try {
            val operation = PointerByReference()
            // RequestVerificationForWindowAsync(appWindow, message, riid, asyncOperation)
            WinRt.succeeded(
                interop.invoke(INTEROP_REQUEST_FOR_WINDOW, hwnd, text, IID_ASYNC_VERIFICATION_RESULT, operation),
            )
            WinRt.asyncIntResult(WinRt.ComObject(operation.value)) == VERIFIED
        } finally {
            WinRt.deleteString(text)
            interop.release()
        }
    }

    private fun call(what: String, block: () -> Boolean): Boolean = try {
        block()
    } catch (e: Throwable) {
        logcat(LogPriority.WARN, e) { "Windows Hello: couldn't $what" }
        false
    }

    private const val CLASS_NAME = "Windows.Security.Credentials.UI.UserConsentVerifier"

    // From the Windows SDK 10.0.26100: UserConsentVerifierInterop.idl (Windows 10 1709+) and
    // windows.security.credentials.ui.h, where IAsyncOperation<UserConsentVerificationResult> is declared
    private val IID_INTEROP = GUID.fromString("{39E050C3-4E74-441A-8DC0-B81104DF949C}")
    private val IID_STATICS = GUID.fromString("{AF4F3F91-564C-4DDC-B8B5-973447627C65}")
    private val IID_ASYNC_VERIFICATION_RESULT = GUID.fromString("{FD596FFD-2318-558F-9DBE-D21DF43764A5}")

    private const val STATICS_CHECK_AVAILABILITY = 6
    private const val INTEROP_REQUEST_FOR_WINDOW = 6

    private const val AVAILABLE = 0 // UserConsentVerifierAvailability.Available
    private const val VERIFIED = 0 // UserConsentVerificationResult.Verified
}

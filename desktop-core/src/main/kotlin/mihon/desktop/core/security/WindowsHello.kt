package mihon.desktop.core.security

import com.sun.jna.Function
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Guid.GUID
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * Windows Hello through WinRT's UserConsentVerifier (spec 11b §2): the PIN, fingerprint or face dialog of the
 * current Windows user. Blocking calls, for an IO thread; any COM failure counts as unavailable or not verified.
 */
object WindowsHello {
    fun isAvailable(): Boolean = call("check availability") {
        val statics = activationFactory(IID_STATICS)
        try {
            val operation = PointerByReference()
            succeeded(statics.invoke(STATICS_CHECK_AVAILABILITY, operation)) // CheckAvailabilityAsync
            asyncIntResult(ComObject(operation.value)) == AVAILABLE
        } finally {
            statics.release()
        }
    }

    /** True only when the user verified; [hwnd] owns the dialog, so it opens in front of the window. */
    fun verify(hwnd: Pointer, message: String): Boolean = call("verify") {
        val interop = activationFactory(IID_INTEROP)
        val text = hstring(message)
        try {
            val operation = PointerByReference()
            // RequestVerificationForWindowAsync(appWindow, message, riid, asyncOperation)
            succeeded(interop.invoke(INTEROP_REQUEST_FOR_WINDOW, hwnd, text, IID_ASYNC_VERIFICATION_RESULT, operation))
            asyncIntResult(ComObject(operation.value)) == VERIFIED
        } finally {
            Combase.INSTANCE.WindowsDeleteString(text)
            interop.release()
        }
    }

    private fun call(what: String, block: () -> Boolean): Boolean = try {
        val init = Combase.INSTANCE.RoInitialize(RO_INIT_MULTITHREADED)
        // A thread already in another apartment can still make these calls
        if (init < 0 && init != RPC_E_CHANGED_MODE) error("RoInitialize 0x%08x".format(init))
        block()
    } catch (e: Throwable) {
        logcat(LogPriority.WARN, e) { "Windows Hello: couldn't $what" }
        false
    }

    private fun activationFactory(iid: GUID): ComObject {
        val className = hstring(CLASS_NAME)
        try {
            val factory = PointerByReference()
            succeeded(Combase.INSTANCE.RoGetActivationFactory(className, iid, factory))
            return ComObject(factory.value)
        } finally {
            Combase.INSTANCE.WindowsDeleteString(className)
        }
    }

    /** Waits for an IAsyncOperation<enum> to end, polling its IAsyncInfo, and returns its result; releases it. */
    private fun asyncIntResult(operation: ComObject): Int {
        try {
            val infoRef = PointerByReference()
            succeeded(operation.invoke(QUERY_INTERFACE, IID_ASYNC_INFO, infoRef))
            val info = ComObject(infoRef.value)
            try {
                val status = IntByReference()
                while (true) {
                    succeeded(info.invoke(ASYNC_INFO_STATUS, status))
                    if (status.value != ASYNC_STARTED) break
                    Thread.sleep(POLL_MS)
                }
                check(status.value == ASYNC_COMPLETED) { "async status ${status.value}" }
            } finally {
                info.release()
            }
            val result = IntByReference()
            succeeded(operation.invoke(ASYNC_OPERATION_GET_RESULTS, result))
            return result.value
        } finally {
            operation.release()
        }
    }

    private fun hstring(text: String): Pointer {
        val ref = PointerByReference()
        succeeded(Combase.INSTANCE.WindowsCreateString(WString(text), text.length, ref))
        return ref.value
    }

    private fun succeeded(hresult: Int) = check(hresult >= 0) { "HRESULT 0x%08x".format(hresult) }

    /** A COM interface pointer: calls go through its vtable. */
    private class ComObject(private val pointer: Pointer) {
        fun invoke(slot: Int, vararg args: Any): Int {
            val entry = pointer.getPointer(0).getPointer(slot.toLong() * Native.POINTER_SIZE)
            return Function.getFunction(entry, Function.ALT_CONVENTION).invokeInt(arrayOf(pointer, *args))
        }

        fun release() {
            invoke(RELEASE)
        }
    }

    @Suppress("FunctionName")
    private interface Combase : StdCallLibrary {
        fun RoInitialize(type: Int): Int
        fun RoGetActivationFactory(classId: Pointer, iid: GUID, factory: PointerByReference): Int
        fun WindowsCreateString(source: WString, length: Int, string: PointerByReference): Int
        fun WindowsDeleteString(string: Pointer): Int

        companion object {
            val INSTANCE: Combase = Native.load("combase", Combase::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }
    }

    private const val CLASS_NAME = "Windows.Security.Credentials.UI.UserConsentVerifier"

    // From the Windows SDK 10.0.26100: UserConsentVerifierInterop.idl (Windows 10 1709+), asyncinfo.idl and
    // windows.security.credentials.ui.h, where IAsyncOperation<UserConsentVerificationResult> is declared
    private val IID_INTEROP = GUID.fromString("{39E050C3-4E74-441A-8DC0-B81104DF949C}")
    private val IID_STATICS = GUID.fromString("{AF4F3F91-564C-4DDC-B8B5-973447627C65}")
    private val IID_ASYNC_INFO = GUID.fromString("{00000036-0000-0000-C000-000000000046}")
    private val IID_ASYNC_VERIFICATION_RESULT = GUID.fromString("{FD596FFD-2318-558F-9DBE-D21DF43764A5}")

    // vtable slots: IUnknown 0-2, IInspectable 3-5, then each interface's own methods
    private const val QUERY_INTERFACE = 0
    private const val RELEASE = 2
    private const val STATICS_CHECK_AVAILABILITY = 6
    private const val INTEROP_REQUEST_FOR_WINDOW = 6
    private const val ASYNC_INFO_STATUS = 7
    private const val ASYNC_OPERATION_GET_RESULTS = 8

    private const val RO_INIT_MULTITHREADED = 1
    private const val RPC_E_CHANGED_MODE = 0x80010106.toInt()
    private const val ASYNC_STARTED = 0
    private const val ASYNC_COMPLETED = 1
    private const val AVAILABLE = 0 // UserConsentVerifierAvailability.Available
    private const val VERIFIED = 0 // UserConsentVerificationResult.Verified
    private const val POLL_MS = 50L
}

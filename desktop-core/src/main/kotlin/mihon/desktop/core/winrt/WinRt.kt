package mihon.desktop.core.winrt

import com.sun.jna.Function
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Guid.GUID
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions

/**
 * The few WinRT calls desktop-core makes, through JNA and the interfaces' vtables (specs 11b and 11f). Blocking, for an
 * IO thread; every failed HRESULT throws.
 */
internal object WinRt {
    /** Joins the multithreaded apartment; a thread already in another one can still make these calls. */
    fun initialize() {
        val init = Combase.INSTANCE.RoInitialize(RO_INIT_MULTITHREADED)
        if (init < 0 && init != RPC_E_CHANGED_MODE) error("RoInitialize 0x%08x".format(init))
    }

    fun activationFactory(className: String, iid: GUID): ComObject {
        val name = hstring(className)
        try {
            val factory = PointerByReference()
            succeeded(Combase.INSTANCE.RoGetActivationFactory(name, iid, factory))
            return ComObject(factory.value)
        } finally {
            deleteString(name)
        }
    }

    /** Waits for an IAsyncOperation<enum> to end, polling its IAsyncInfo, and returns its result; releases it. */
    fun asyncIntResult(operation: ComObject): Int {
        try {
            val info = operation.queryInterface(IID_ASYNC_INFO)
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

    fun hstring(text: String): Pointer {
        val ref = PointerByReference()
        succeeded(Combase.INSTANCE.WindowsCreateString(WString(text), text.length, ref))
        return ref.value
    }

    fun deleteString(string: Pointer) {
        Combase.INSTANCE.WindowsDeleteString(string)
    }

    fun succeeded(hresult: Int) = check(hresult >= 0) { "HRESULT 0x%08x".format(hresult) }

    /** A COM interface pointer: calls go through its vtable. */
    class ComObject(private val pointer: Pointer) {
        fun invoke(slot: Int, vararg args: Any): Int {
            val entry = pointer.getPointer(0).getPointer(slot.toLong() * Native.POINTER_SIZE)
            return Function.getFunction(entry, Function.ALT_CONVENTION).invokeInt(arrayOf(pointer, *args))
        }

        fun queryInterface(iid: GUID): ComObject {
            val ref = PointerByReference()
            succeeded(invoke(QUERY_INTERFACE, iid, ref))
            return ComObject(ref.value)
        }

        /** The interface an out parameter at [slot] returns, or null when it returns none. */
        fun interfaceAt(slot: Int): ComObject? {
            val ref = PointerByReference()
            succeeded(invoke(slot, ref))
            return ref.value?.let(::ComObject)
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

    // asyncinfo.idl in the Windows SDK 10.0.26100
    private val IID_ASYNC_INFO = GUID.fromString("{00000036-0000-0000-C000-000000000046}")

    // vtable slots: IUnknown 0-2, IInspectable 3-5, then each interface's own methods
    private const val QUERY_INTERFACE = 0
    private const val RELEASE = 2
    private const val ASYNC_INFO_STATUS = 7
    private const val ASYNC_OPERATION_GET_RESULTS = 8

    private const val RO_INIT_MULTITHREADED = 1
    private const val RPC_E_CHANGED_MODE = 0x80010106.toInt()
    private const val ASYNC_STARTED = 0
    private const val ASYNC_COMPLETED = 1
    private const val POLL_MS = 50L
}

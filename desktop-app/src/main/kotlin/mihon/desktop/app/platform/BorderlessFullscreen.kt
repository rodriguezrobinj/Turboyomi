package mihon.desktop.app.platform

import com.sun.jna.Function
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.COM.COMUtils
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WTypes
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.PointerByReference
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.awt.Frame

/**
 * Fullscreen the way browsers do it on Windows: the frame loses its title bar and borders and covers its monitor,
 * which Windows then treats as a fullscreen app and puts the taskbar behind. AWT's own fullscreen
 * (GraphicsDevice.setFullScreenWindow, what Compose's WindowPlacement.Fullscreen uses) keeps the title bar of a
 * decorated frame and leaves the taskbar over its bottom edge.
 * ITaskbarList2.MarkFullscreenWindow keeps the taskbar behind it while focus briefly leaves (spec 12 §7).
 */
object BorderlessFullscreen {
    /** What [exit] puts back. */
    class Saved internal constructor(
        internal val style: Int,
        internal val exStyle: Int,
        internal val rect: WinDef.RECT,
        internal val maximized: Boolean,
    )

    fun enter(frame: Frame): Saved {
        val hwnd = hwndOf(frame)
        val maximized = frame.extendedState and Frame.MAXIMIZED_BOTH == Frame.MAXIMIZED_BOTH
        // The restored bounds are the ones to come back to
        if (maximized) user32.SendMessage(hwnd, WM_SYSCOMMAND, WinDef.WPARAM(SC_RESTORE), WinDef.LPARAM(0))
        val rect = WinDef.RECT().also { user32.GetWindowRect(hwnd, it) }
        val saved = Saved(
            style = user32.GetWindowLong(hwnd, WinUser.GWL_STYLE),
            exStyle = user32.GetWindowLong(hwnd, WinUser.GWL_EXSTYLE),
            rect = rect,
            maximized = maximized,
        )
        user32.SetWindowLong(hwnd, WinUser.GWL_STYLE, saved.style and (WS_CAPTION or WS_THICKFRAME).inv())
        user32.SetWindowLong(hwnd, WinUser.GWL_EXSTYLE, saved.exStyle and EX_EDGES.inv())
        val monitor = WinUser.MONITORINFO()
        user32.GetMonitorInfo(user32.MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST), monitor)
        monitor.rcMonitor.run { setPos(hwnd, left, top, right - left, bottom - top) }
        markFullscreen(hwnd, true)
        return saved
    }

    fun exit(frame: Frame, saved: Saved) {
        val hwnd = hwndOf(frame)
        markFullscreen(hwnd, false)
        user32.SetWindowLong(hwnd, WinUser.GWL_STYLE, saved.style)
        user32.SetWindowLong(hwnd, WinUser.GWL_EXSTYLE, saved.exStyle)
        saved.rect.run { setPos(hwnd, left, top, right - left, bottom - top) }
        if (saved.maximized) user32.SendMessage(hwnd, WM_SYSCOMMAND, WinDef.WPARAM(SC_MAXIMIZE), WinDef.LPARAM(0))
    }

    /** The taskbar's own fullscreen flag, through COM; a failure only leaves Windows' own guess. */
    private fun markFullscreen(hwnd: WinDef.HWND, fullscreen: Boolean) {
        val init = Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED)
        try {
            val ref = PointerByReference()
            COMUtils.checkRC(
                Ole32.INSTANCE.CoCreateInstance(
                    CLSID_TASKBAR_LIST,
                    null,
                    WTypes.CLSCTX_INPROC_SERVER,
                    IID_TASKBAR_LIST2,
                    ref,
                ),
            )
            val taskbar = ref.value
            try {
                COMUtils.checkRC(WinNT.HRESULT(vtableCall(taskbar, HR_INIT)))
                COMUtils.checkRC(
                    WinNT.HRESULT(vtableCall(taskbar, MARK_FULLSCREEN_WINDOW, hwnd, if (fullscreen) 1 else 0)),
                )
            } finally {
                vtableCall(taskbar, RELEASE)
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "MarkFullscreenWindow failed" }
        } finally {
            if (COMUtils.SUCCEEDED(init)) Ole32.INSTANCE.CoUninitialize()
        }
    }

    private fun vtableCall(com: Pointer, slot: Int, vararg args: Any): Int {
        val entry = com.getPointer(0).getPointer(slot.toLong() * Native.POINTER_SIZE)
        return Function.getFunction(entry, Function.ALT_CONVENTION).invokeInt(arrayOf(com, *args))
    }

    private fun hwndOf(frame: Frame) = WinDef.HWND(Native.getWindowPointer(frame))

    private fun setPos(hwnd: WinDef.HWND, x: Int, y: Int, width: Int, height: Int) {
        user32.SetWindowPos(hwnd, null, x, y, width, height, SWP_NOZORDER or SWP_NOACTIVATE or SWP_FRAMECHANGED)
    }

    private val user32 get() = User32.INSTANCE

    private const val WS_CAPTION = 0x00C00000
    private const val WS_THICKFRAME = 0x00040000

    // WS_EX_DLGMODALFRAME, WS_EX_WINDOWEDGE, WS_EX_CLIENTEDGE, WS_EX_STATICEDGE
    private const val EX_EDGES = 0x00000001 or 0x00000100 or 0x00000200 or 0x00020000
    private const val SWP_NOZORDER = 0x0004
    private const val SWP_NOACTIVATE = 0x0010
    private const val SWP_FRAMECHANGED = 0x0020
    private const val MONITOR_DEFAULTTONEAREST = 2
    private const val WM_SYSCOMMAND = 0x0112
    private const val SC_RESTORE = 0xF120L
    private const val SC_MAXIMIZE = 0xF030L

    // shobjidl_core.h; ITaskbarList2's vtable: IUnknown 0-2, HrInit 3, AddTab to SetActiveAlt 4-7, then 8
    private val CLSID_TASKBAR_LIST = Guid.CLSID("{56FDF344-FD6D-11d0-958A-006097C9A090}")
    private val IID_TASKBAR_LIST2 = Guid.IID("{602D4995-B13A-429b-A66E-1935E44F4317}")
    private const val RELEASE = 2
    private const val HR_INIT = 3
    private const val MARK_FULLSCREEN_WINDOW = 8
}

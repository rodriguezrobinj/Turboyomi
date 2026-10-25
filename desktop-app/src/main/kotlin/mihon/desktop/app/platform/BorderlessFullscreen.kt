package mihon.desktop.app.platform

import com.sun.jna.Native
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import java.awt.Frame

/**
 * Fullscreen the way browsers do it on Windows: the frame loses its title bar and borders and covers its monitor,
 * which Windows then treats as a fullscreen app and puts the taskbar behind. AWT's own fullscreen
 * (GraphicsDevice.setFullScreenWindow, what Compose's WindowPlacement.Fullscreen uses) keeps the title bar of a
 * decorated frame and leaves the taskbar over its bottom edge.
 * ponytail: no ITaskbarList2.MarkFullscreenWindow; add it if the taskbar ever shows over the window.
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
        return saved
    }

    fun exit(frame: Frame, saved: Saved) {
        val hwnd = hwndOf(frame)
        user32.SetWindowLong(hwnd, WinUser.GWL_STYLE, saved.style)
        user32.SetWindowLong(hwnd, WinUser.GWL_EXSTYLE, saved.exStyle)
        saved.rect.run { setPos(hwnd, left, top, right - left, bottom - top) }
        if (saved.maximized) user32.SendMessage(hwnd, WM_SYSCOMMAND, WinDef.WPARAM(SC_MAXIMIZE), WinDef.LPARAM(0))
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
}

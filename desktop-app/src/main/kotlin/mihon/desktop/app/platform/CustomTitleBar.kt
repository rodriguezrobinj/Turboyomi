package mihon.desktop.app.platform

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.awt.Window

/**
 * The window's frame without Windows' title bar, under the app's own [WindowTitleBar]: Windows 10 only paints its bar
 * black or white. The window keeps its borders, shadow, Aero Snap and system menu, and the bar's area answers Windows
 * as a title bar would, so Windows still moves, snaps and maximizes the window from it.
 */
object CustomTitleBar {
    /** Windows 11 paints its own bar in the theme's colors ([TitleBarColors]); the JDK names it apart since 18. */
    val needed: Boolean = System.getProperty("os.name") == "Windows 10"

    /** Where the app's bar is, set by its layout. Read on AWT's toolkit thread, where the window procedures run. */
    @Volatile
    var geometry: TitleBarGeometry? = null

    // JNA frees a callback nothing references, and Windows would call into freed code
    private val procedures = mutableListOf<WinUser.WindowProc>()

    fun install(window: Window) {
        if (!window.isDisplayable) return
        val frame = WinDef.HWND(Native.getWindowPointer(window))
        subclass(frame) { original, hwnd, msg, wParam, lParam -> frameProcedure(original, hwnd, msg, wParam, lParam) }
        // Compose's canvas is a child window over the whole client area: Windows asks it, not the frame, about the mouse
        User32.INSTANCE.EnumChildWindows(
            frame,
            { child, _ ->
                subclass(child) { original, hwnd, msg, wParam, lParam ->
                    if (msg == WM_NCHITTEST && hasCaption(frame) && hit(frame, lParam) != HTCLIENT) {
                        // Windows then asks the frame underneath
                        WinDef.LRESULT(HTTRANSPARENT)
                    } else {
                        user32.CallWindowProc(original, hwnd, msg, wParam, lParam)
                    }
                }
                true
            },
            null,
        )
        User32.INSTANCE.SetWindowPos(
            frame,
            null,
            0,
            0,
            0,
            0,
            SWP_FRAMECHANGED or SWP_NOMOVE or SWP_NOSIZE or SWP_NOZORDER or SWP_NOACTIVATE,
        )
    }

    private fun frameProcedure(
        original: Pointer,
        hwnd: WinDef.HWND,
        msg: Int,
        wParam: WinDef.WPARAM,
        lParam: WinDef.LPARAM,
    ): WinDef.LRESULT {
        // Without a caption (fullscreen) the frame is Windows' again
        if (!hasCaption(hwnd)) return user32.CallWindowProc(original, hwnd, msg, wParam, lParam)
        return when {
            msg == WM_NCCALCSIZE && wParam.toLong() != 0L -> {
                // NCCALCSIZE_PARAMS starts with the window's RECT, which Windows turns into the client area's: the client
                // area takes Windows' bar back and the frame keeps its other three sides
                val rect = Pointer(lParam.toLong())
                val top = rect.getInt(RECT_TOP)
                val result = user32.CallWindowProc(original, hwnd, msg, wParam, lParam)
                // Maximized, Windows puts the frame's thickness off screen: the content starts below it
                rect.setInt(RECT_TOP, if (user32.IsZoomed(hwnd)) top + border(hwnd) else top)
                result
            }
            msg == WM_NCHITTEST -> {
                val result = user32.CallWindowProc(original, hwnd, msg, wParam, lParam)
                if (result.toInt() == HTCLIENT) WinDef.LRESULT(hit(hwnd, lParam).toLong()) else result
            }
            else -> user32.CallWindowProc(original, hwnd, msg, wParam, lParam)
        }
    }

    private fun subclass(
        hwnd: WinDef.HWND,
        procedure: (Pointer, WinDef.HWND, Int, WinDef.WPARAM, WinDef.LPARAM) -> WinDef.LRESULT,
    ) {
        // Read first: AWT's thread may call the new procedure as soon as it's set
        val original = user32.GetWindowLongPtr(hwnd, GWLP_WNDPROC)
        val callback = WinUser.WindowProc { h, msg, wParam, lParam ->
            try {
                procedure(original, h, msg, wParam, lParam)
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e) { "Title bar window procedure failed" }
                user32.CallWindowProc(original, h, msg, wParam, lParam)
            }
        }
        procedures += callback
        user32.SetWindowLongPtr(hwnd, GWLP_WNDPROC, callback)
    }

    private fun hit(frame: WinDef.HWND, lParam: WinDef.LPARAM): Int {
        // Screen coordinates, signed: a second monitor can be left of or above the first
        val packed = lParam.toLong().toInt()
        val point = WinDef.POINT(packed.toShort().toInt(), (packed shr 16).toShort().toInt())
        user32.ScreenToClient(frame, point)
        val client = WinDef.RECT().also { User32.INSTANCE.GetClientRect(frame, it) }
        return titleBarHit(point.x, point.y, client.right, geometry, border(frame), user32.IsZoomed(frame))
    }

    private fun hasCaption(hwnd: WinDef.HWND) =
        User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_STYLE) and WS_CAPTION == WS_CAPTION

    /** The frame's thickness at the window's DPI: what Windows' own bar would resize from. */
    private fun border(hwnd: WinDef.HWND): Int {
        val dpi = user32.GetDpiForWindow(hwnd)
        return user32.GetSystemMetricsForDpi(SM_CYFRAME, dpi) + user32.GetSystemMetricsForDpi(SM_CXPADDEDBORDER, dpi)
    }

    private val user32 get() = User32Frame.INSTANCE

    @Suppress("FunctionName")
    private interface User32Frame : StdCallLibrary {
        fun GetWindowLongPtr(hwnd: WinDef.HWND, index: Int): Pointer
        fun SetWindowLongPtr(hwnd: WinDef.HWND, index: Int, procedure: WinUser.WindowProc): Pointer
        fun CallWindowProc(
            procedure: Pointer,
            hwnd: WinDef.HWND,
            msg: Int,
            wParam: WinDef.WPARAM,
            lParam: WinDef.LPARAM,
        ): WinDef.LRESULT
        fun ScreenToClient(hwnd: WinDef.HWND, point: WinDef.POINT): Boolean
        fun IsZoomed(hwnd: WinDef.HWND): Boolean
        fun GetDpiForWindow(hwnd: WinDef.HWND): Int
        fun GetSystemMetricsForDpi(index: Int, dpi: Int): Int

        companion object {
            val INSTANCE: User32Frame = Native.load("user32", User32Frame::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }
    }

    private const val GWLP_WNDPROC = -4
    private const val WM_NCCALCSIZE = 0x0083
    private const val WM_NCHITTEST = 0x0084
    private const val HTTRANSPARENT = -1L
    private const val WS_CAPTION = 0x00C00000
    private const val RECT_TOP = 4L
    private const val SM_CYFRAME = 33
    private const val SM_CXPADDEDBORDER = 92
    private const val SWP_NOSIZE = 0x0001
    private const val SWP_NOMOVE = 0x0002
    private const val SWP_NOZORDER = 0x0004
    private const val SWP_NOACTIVATE = 0x0010
    private const val SWP_FRAMECHANGED = 0x0020
}

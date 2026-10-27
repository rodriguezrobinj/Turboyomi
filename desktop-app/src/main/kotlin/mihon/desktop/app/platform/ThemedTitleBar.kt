package mihon.desktop.app.platform

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.LocalAwtWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.sun.jna.Native
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.awt.Window

/** The window's title bar in the theme's colors, following it when the theme changes. Nothing outside a window. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ThemedTitleBar() {
    val window = LocalAwtWindow.current ?: return
    val colors = MaterialTheme.colorScheme
    LaunchedEffect(window, colors.surface, colors.onSurface) {
        TitleBarColors.apply(window, colors.surface, colors.onSurface)
    }
}

/**
 * Windows 11 paints the title bar in any color (DWM); Windows 10 only has its dark or light bar, picked here from
 * the background's luminance.
 */
object TitleBarColors {
    fun apply(window: Window, background: Color, text: Color) {
        if (!window.isDisplayable) return
        val hwnd = WinDef.HWND(Native.getWindowPointer(window))
        set(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, if (background.luminance() < 0.5f) 1 else 0)
        // Windows 10 refuses these two (E_INVALIDARG) and keeps the dark or light bar
        set(hwnd, DWMWA_CAPTION_COLOR, background.toColorRef())
        set(hwnd, DWMWA_TEXT_COLOR, text.toColorRef())
        // Windows 10 repaints the bar only on an activation change (SWP_FRAMECHANGED isn't enough): fake one
        ncActivate(hwnd, !window.isActive)
        ncActivate(hwnd, window.isActive)
    }

    private fun ncActivate(hwnd: WinDef.HWND, active: Boolean) {
        User32.INSTANCE.SendMessage(hwnd, WM_NCACTIVATE, WinDef.WPARAM(if (active) 1L else 0L), WinDef.LPARAM(0))
    }

    private fun set(hwnd: WinDef.HWND, attribute: Int, value: Int) {
        Dwmapi.INSTANCE.DwmSetWindowAttribute(hwnd, attribute, IntByReference(value), Int.SIZE_BYTES)
    }

    // COLORREF is 0x00BBGGRR
    private fun Color.toColorRef(): Int {
        val argb = toArgb()
        return (argb and 0xFF shl 16) or (argb and 0xFF00) or (argb shr 16 and 0xFF)
    }

    @Suppress("FunctionName")
    private interface Dwmapi : StdCallLibrary {
        fun DwmSetWindowAttribute(hwnd: WinDef.HWND, attribute: Int, value: IntByReference, size: Int): WinNT.HRESULT

        companion object {
            val INSTANCE: Dwmapi = Native.load("dwmapi", Dwmapi::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }
    }

    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMWA_CAPTION_COLOR = 35
    private const val DWMWA_TEXT_COLOR = 36
    private const val WM_NCACTIVATE = 0x0086
}

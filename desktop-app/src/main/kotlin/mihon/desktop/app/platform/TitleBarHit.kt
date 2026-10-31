package mihon.desktop.app.platform

/** Where the app's own title bar is, in the window's client pixels: from the top, its buttons up to the right edge. */
data class TitleBarGeometry(val height: Int, val buttonsStart: Int)

/**
 * What Windows does with the mouse at ([x], [y]), client pixels, in a window [width] wide with the app's own title
 * bar: resize from the top edge, [border] thick and not when maximized; move the window from the bar outside its
 * buttons; else leave it to the app.
 */
fun titleBarHit(x: Int, y: Int, width: Int, bar: TitleBarGeometry?, border: Int, maximized: Boolean): Int = when {
    !maximized && y < border -> when {
        x < border -> HTTOPLEFT
        x >= width - border -> HTTOPRIGHT
        else -> HTTOP
    }
    bar != null && y < bar.height && x < bar.buttonsStart -> HTCAPTION
    else -> HTCLIENT
}

// WM_NCHITTEST's answers
internal const val HTCLIENT = 1
internal const val HTCAPTION = 2
internal const val HTTOP = 12
internal const val HTTOPLEFT = 13
internal const val HTTOPRIGHT = 14

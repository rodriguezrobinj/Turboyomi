package mihon.desktop.core.cef

import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.name

/**
 * The embedded browser's profile in [dir] (cache, storage, history): "Clear browser data" in Settings › Advanced.
 * While Chromium runs it holds those files, so then they go at the next start, like [CefInstaller.uninstall].
 */
@OptIn(ExperimentalPathApi::class)
class CefData(private val dir: Path) {
    private val marker: Path get() = dir.resolveSibling("${dir.name}.clear")

    /** Returns whether the data is gone now; false means it goes at the next start. */
    fun clear(running: Boolean): Boolean {
        if (running) return markForClearing()
        return try {
            dir.deleteRecursively()
            marker.deleteIfExists()
            true
        } catch (_: IOException) {
            markForClearing()
        }
    }

    /** At startup, before the browser starts: finishes a clearing asked for while it ran. */
    fun applyPending() {
        if (marker.exists()) clear(running = false)
    }

    private fun markForClearing(): Boolean {
        if (!marker.exists()) marker.createFile()
        return false
    }
}

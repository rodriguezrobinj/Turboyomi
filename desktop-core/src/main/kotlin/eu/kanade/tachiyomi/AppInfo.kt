package eu.kanade.tachiyomi

import mihon.desktop.core.DesktopBuildInfo

/**
 * Used by extensions.
 */
@Suppress("UNUSED")
object AppInfo {
    /** @since extension-lib 1.3 */
    fun getVersionCode(): Int = DesktopBuildInfo.VERSION_CODE

    /** @since extension-lib 1.3 */
    fun getVersionName(): String = DesktopBuildInfo.VERSION_NAME

    /** Formats the v1 reader decodes (spec 4.3). @since extension-lib 1.5 */
    fun getSupportedImageMimeTypes(): List<String> =
        listOf("image/jpeg", "image/png", "image/webp", "image/gif", "image/bmp")
}

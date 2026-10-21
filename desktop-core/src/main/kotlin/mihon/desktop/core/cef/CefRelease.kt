package mihon.desktop.core.cef

/** The embedded browser's build (spec 2b §2): JetBrains Runtime's JCEF, the release Suwayomi pins for this JCEF. */
object CefRelease {
    const val JBR_RELEASE = "jbr-release-25.0.4b508.27"
    const val URL = "https://cache-redirector.jetbrains.com/intellij-jbr/jbr_jcef-25.0.4-windows-x64-b508.27.zip"

    /** The jcef library in gradle/libs.versions.toml: the natives must be its build (CefReleaseTest checks it). */
    const val JCEF_VERSION = "144.0.15-g72717cf-chromium-144.0.7559.172-api-1.21-262-b37"
}

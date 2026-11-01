package mihon.desktop.core.cef

/** The embedded browser's build (spec 2b §2): JetBrains Runtime's JCEF, the release Suwayomi pins for this JCEF. */
object CefRelease {
    const val JBR_RELEASE = "jbr-release-25.0.4b508.27"
    const val URL = "https://cache-redirector.jetbrains.com/intellij-jbr/jbr_jcef-25.0.4-windows-x64-b508.27.zip"

    /**
     * [URL]'s SHA-512, kept here rather than read from the server's .checksum next to it, which whoever could change
     * the zip could change too. A new [URL] needs its own: `curl -sL "<URL>.checksum"`.
     */
    const val SHA512 = "22a9af5b857ab693240d7015955a5e49351c64f01f341876ba150d4f2721c221" +
        "6ec7ac4997a38eb74caf8c2b85b2392aa3a6bfe0bf72955522d20f6f7f6dd827"

    /** The jcef library in gradle/libs.versions.toml: the natives must be its build (CefReleaseTest checks it). */
    const val JCEF_VERSION = "144.0.15-g72717cf-chromium-144.0.7559.172-api-1.21-262-b37"
}

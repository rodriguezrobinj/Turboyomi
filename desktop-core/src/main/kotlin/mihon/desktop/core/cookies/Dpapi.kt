package mihon.desktop.core.cookies

import com.sun.jna.platform.win32.Crypt32Util

/** Windows' DPAPI: what it protects only the same Windows user can read (spec 2b §4, the cookie file). */
object Dpapi {
    fun protect(data: ByteArray): ByteArray = Crypt32Util.cryptProtectData(data)

    fun unprotect(data: ByteArray): ByteArray = Crypt32Util.cryptUnprotectData(data)
}

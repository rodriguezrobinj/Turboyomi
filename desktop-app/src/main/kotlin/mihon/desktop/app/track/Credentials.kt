package mihon.desktop.app.track

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import logcat.LogPriority
import mihon.desktop.core.cookies.Dpapi
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.util.system.logcat
import java.util.Base64

/**
 * The trackers' credentials, encrypted with DPAPI for this Windows user (parent spec §5.5): never stored in clear.
 * A backup carries them in clear, as Mihon does, since a DPAPI blob is useless on another machine (spec 5b-1 §3 E).
 */
object Credentials {
    private const val PREFIX = "dpapi:"
    private val KEY = Regex(
        "^" + Regex.escape(Preference.privateKey("")) +
            "(pref_mangasync_username_|pref_mangasync_password_|track_token_)\\d+$",
    )

    fun isCredentialKey(key: String): Boolean = KEY.matches(key)

    fun isSealed(stored: String): Boolean = stored.startsWith(PREFIX)

    fun seal(plain: String): String =
        if (plain.isEmpty()) {
            ""
        } else {
            PREFIX +
                Base64.getEncoder().encodeToString(Dpapi.protect(plain.encodeToByteArray()))
        }

    /** The clear value: a sealed one decrypted, or one stored in clear (restored, or from before sealing) as it is. */
    fun unseal(stored: String): String {
        if (!isSealed(stored)) return stored
        return try {
            Dpapi.unprotect(Base64.getDecoder().decode(stored.removePrefix(PREFIX))).decodeToString()
        } catch (e: Exception) {
            // Another Windows user or a reinstalled profile: the session is gone, sign in again (spec 5b-1 §4)
            logcat(LogPriority.WARN, e) { "A tracker credential couldn't be decrypted" }
            ""
        }
    }
}

/** [inner] with its value sealed by [Credentials]; one found in clear is sealed on the first read. */
class SealedPreference(private val inner: Preference<String>) : Preference<String> {
    override fun key(): String = inner.key()

    override fun get(): String {
        val stored = inner.get()
        if (stored.isNotEmpty() && !Credentials.isSealed(stored)) inner.set(Credentials.seal(stored))
        return Credentials.unseal(stored)
    }

    override fun set(value: String) = inner.set(Credentials.seal(value))

    override fun isSet(): Boolean = inner.isSet()

    override fun delete() = inner.delete()

    override fun defaultValue(): String = inner.defaultValue()

    override fun changes(): Flow<String> = inner.changes().map(Credentials::unseal)

    override fun stateIn(scope: CoroutineScope): StateFlow<String> =
        changes().stateIn(scope, SharingStarted.Eagerly, get())
}

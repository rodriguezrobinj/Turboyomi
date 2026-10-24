package mihon.desktop.app.track

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * The OAuth logins opened from this app, by service, with the state their redirect must bring back (spec 5b-2 §3 A,
 * §4). In memory only: a login opened before the app closed has to be started again.
 */
@Inject
@SingleIn(AppScope::class)
class PendingLogins {
    enum class Take { Ok, None, WrongState }

    private val states = ConcurrentHashMap<String, String>()

    fun open(service: String): String {
        val bytes = ByteArray(24).also(SecureRandom()::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).also { states[service] = it }
    }

    /** Closes [service]'s pending login if [state] matches; services that bring no state back pass null. */
    fun take(service: String, state: String?): Take {
        val expected = states[service] ?: return Take.None
        if (state != null && state != expected) return Take.WrongState
        states.remove(service)
        return Take.Ok
    }
}

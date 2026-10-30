package mihon.desktop.app.security

import eu.kanade.tachiyomi.core.security.SecurityPreferences
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class AppLockTest {
    private val preferences = SecurityPreferences(InMemoryPreferenceStore())
    private var time = 0L
    private var available = true
    private val lock = AppLock(preferences, isAvailable = { available }, now = { time })

    private fun enable(lockAfter: Int) {
        preferences.useAuthenticator.set(true)
        preferences.lockAppAfter.set(lockAfter)
    }

    private fun unlocked() = runBlocking { lock.unlock { true } }

    @Test
    fun `turned off, the app never locks`() {
        lock.onStart()
        lock.locked.value shouldBe false
    }

    @Test
    fun `without Windows Hello the app doesn't lock, or nobody could open it`() {
        enable(0)
        available = false
        lock.onStart()
        lock.locked.value shouldBe false
    }

    @Test
    fun `it starts locked and a failed check keeps it locked`() {
        enable(-1)
        lock.onStart()
        lock.locked.value shouldBe true
        runBlocking { lock.unlock { false } } shouldBe false
        lock.locked.value shouldBe true
        unlocked() shouldBe true
        lock.locked.value shouldBe false
    }

    @Test
    fun `never locks again after the start`() {
        enable(-1)
        lock.onStart()
        unlocked()
        lock.onBackground()
        time += 60 * 60_000L
        lock.onForeground()
        lock.locked.value shouldBe false
    }

    @Test
    fun `always locks on coming back`() {
        enable(0)
        lock.onStart()
        unlocked()
        lock.onBackground()
        lock.onForeground()
        lock.locked.value shouldBe true
    }

    @Test
    fun `after N minutes away it locks, before that it doesn't`() {
        enable(2)
        lock.onStart()
        unlocked()
        lock.onBackground()
        time += 60_000L
        lock.onForeground()
        lock.locked.value shouldBe false

        lock.onBackground()
        time += 2 * 60_000L
        lock.onForeground()
        lock.locked.value shouldBe true
    }
}

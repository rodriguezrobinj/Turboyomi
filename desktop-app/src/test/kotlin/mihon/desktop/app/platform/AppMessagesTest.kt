package mihon.desktop.app.platform

import android.content.Context
import android.widget.Toast
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.toast
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppMessagesTest {
    @BeforeAll
    fun start() {
        startTestApp()
        AppMessages.install()
    }

    @Test
    fun `an extension's toast reaches the window's messages`(): Unit = runBlocking {
        // Subscribe before the toast: the flow has no replay
        val received = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5.seconds) { AppMessages.messages.first().text }
        }
        Toast.makeText(Injekt.get<Context>(), "Inicia sesión en José Pérez", Toast.LENGTH_SHORT).show()
        received.await() shouldBe "Inicia sesión en José Pérez"
    }

    @Test
    fun `ported code's context toast reaches them too`(): Unit = runBlocking {
        // Subscribe before the toast: the flow has no replay
        val received = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5.seconds) { AppMessages.messages.first().text }
        }
        Injekt.get<Context>().toast("hola")
        received.await() shouldBe "hola"
    }

    @Test
    fun `locale helper names multi-language sources without android`() {
        LocaleHelper.getLocalizedDisplayName("es").isNotBlank() shouldBe true
    }
}

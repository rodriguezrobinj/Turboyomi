package tachiyomi.i18n

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.Locale

class MRSmokeTest {
    @Test
    fun `strings resolve on the JVM`() {
        MR.strings.app_name.localized(Locale.ENGLISH) shouldBe "Mihon"
    }
}

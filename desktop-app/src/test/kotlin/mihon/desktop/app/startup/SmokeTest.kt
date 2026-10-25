package mihon.desktop.app.startup

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.io.path.createTempDirectory

class SmokeTest {
    @Test
    fun `the smoke check builds the whole app graph on fresh data and passes`() {
        Smoke.run(createTempDirectory("Humo José")) shouldBe true
    }
}

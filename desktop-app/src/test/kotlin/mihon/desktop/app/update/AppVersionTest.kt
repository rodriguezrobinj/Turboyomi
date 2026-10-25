package mihon.desktop.app.update

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AppVersionTest {
    @Test
    fun `versions compare by number, not by text`() {
        AppVersion.isNewer("v0.10.0", "0.9.0") shouldBe true
        AppVersion.isNewer("v1.0.0", "0.99.99") shouldBe true
        AppVersion.isNewer("0.1.1", "0.1.0") shouldBe true
    }

    @Test
    fun `the same or an older version is no update`() {
        AppVersion.isNewer("v0.1.0", "0.1.0") shouldBe false
        AppVersion.isNewer("v0.0.9", "0.1.0") shouldBe false
    }

    @Test
    fun `a tag that isn't x_y_z is no update`() {
        AppVersion.isNewer("v0.2.0-beta", "0.1.0") shouldBe false
        AppVersion.isNewer("nightly", "0.1.0") shouldBe false
        AppVersion.isNewer("v1.2", "0.1.0") shouldBe false
    }
}

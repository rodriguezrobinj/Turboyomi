package mihon.desktop.app.settings

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR
import java.io.File
import java.util.Locale

class AppLanguageTest {
    @Test
    fun `the list has every translation, so a Mihon sync can't leave one out`() {
        val folders = File("../i18n/src/commonMain/moko-resources").list()!!
            .filter { it != "base" }
            .map { it.replace("-r", "-") }
        AppLanguage.TAGS.filter { it != "en" } shouldContainExactlyInAnyOrder folders
    }

    @Test
    fun `choosing a language changes the strings, and the default goes back to the system's`() {
        val before = Locale.getDefault()
        try {
            AppLanguage.apply("es")
            str(MR.strings.label_settings) shouldBe "Ajustes"
            AppLanguage.apply("en")
            str(MR.strings.label_settings) shouldBe "Settings"
            AppLanguage.apply("")
            Locale.getDefault() shouldBe before
        } finally {
            Locale.setDefault(before)
        }
    }
}

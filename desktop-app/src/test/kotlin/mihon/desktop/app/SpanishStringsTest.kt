package mihon.desktop.app

import io.kotest.matchers.collections.shouldBeEmpty
import org.junit.jupiter.api.Test
import java.io.File

/** Every string the desktop app shows exists in Spanish, so a Spanish Windows never sees English in its screens. */
class SpanishStringsTest {
    private val strings = File("../i18n/src/commonMain/moko-resources")

    // Strings marked translatable="false" (the app's name) are the same in every language
    private fun keys(lang: String): Set<String> = Regex("""<string name="([^"]+)"(?! translatable="false")""")
        .findAll(strings.resolve("$lang/strings.xml").readText())
        .map { it.groupValues[1].replace('.', '_') }
        .toSet()

    @Test
    fun `every string the app uses has a Spanish translation`() {
        val used = listOf(File("src/main/kotlin"))
            .flatMap { dir -> dir.walk().filter { it.extension == "kt" }.toList() }
            .flatMap { file -> Regex("""MR\.strings\.(\w+)""").findAll(file.readText()).map { it.groupValues[1] } }
            .toSet()
        val english = keys("base")
        val spanish = keys("es")

        used.filter { it in english && it !in spanish }.sorted().shouldBeEmpty()
    }
}

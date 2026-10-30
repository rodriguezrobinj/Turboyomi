package eu.kanade.presentation.more.settings.screen.about

import com.mikepenz.aboutlibraries.Libs
import io.kotest.matchers.collections.shouldContain
import org.junit.jupiter.api.Test

class OpenSourceLicensesTest {
    @Test
    fun `the build exports the dependencies with their licenses into the resources`() {
        val json = OpenSourceLicensesScreen::class.java.getResource("/aboutlibraries.json")!!.readText()
        val libraries = Libs.Builder().withJson(json).build().libraries

        libraries.map { it.uniqueId } shouldContain "com.squareup.okhttp3:okhttp"
    }
}

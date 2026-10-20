package mihon.desktop.app.source

import android.content.Context
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.sourcePreferences
import io.kotest.matchers.shouldBe
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * A source with one preference of each kind extensions use, like a real extension's setupPreferenceScreen.
 * Each test passes its own [id]: the preferences persist per source id for the whole JVM.
 */
class ConfigurableFakeSource(override val id: Long) : ConfigurableSource {
    override val name = "Configurable"
    override val lang = "es"
    override val supportsLatest = false
    var lastQuality: Any? = null

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addPreference(
            ListPreference(screen.context).apply {
                key = "quality"
                title = "Calidad"
                summary = "%s"
                entries = arrayOf<CharSequence>("Alta", "Baja")
                entryValues = arrayOf<CharSequence>("high", "low")
                setDefaultValue("high")
                setOnPreferenceChangeListener { _, new ->
                    lastQuality = new
                    true
                }
            },
        )
        screen.addPreference(
            SwitchPreferenceCompat(screen.context).apply {
                key = "split"
                title = "Dividir páginas"
                setDefaultValue(false)
            },
        )
        screen.addPreference(
            CheckBoxPreference(screen.context).apply {
                key = "locked"
                title = "No se puede cambiar"
                setDefaultValue(false)
                setOnPreferenceChangeListener { _, _ -> false }
            },
        )
        screen.addPreference(
            EditTextPreference(screen.context).apply {
                key = "domain"
                title = "Dominio"
                setDefaultValue("ejemplo.com")
            },
        )
        screen.addPreference(
            MultiSelectListPreference(screen.context).apply {
                key = "langs"
                title = "Idiomas"
                entries = arrayOf<CharSequence>("Español", "English")
                entryValues = arrayOf<CharSequence>("es", "en")
                setDefaultValue(setOf("es"))
            },
        )
    }

    override suspend fun getPopularManga(page: Int) = MangasPage(emptyList(), false)
    override suspend fun getLatestUpdates(page: Int) = MangasPage(emptyList(), false)
    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList) = MangasPage(emptyList(), false)
    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ) =
        SMangaUpdate(manga, chapters)
    override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SourcePreferenceModelTest {
    @BeforeAll
    fun start() {
        startTestApp()
    }

    private fun prefs(source: ConfigurableFakeSource) =
        SourcePreferenceModel.load(source, Injekt.get<Context>()).associateBy { it.key }

    @Test
    fun `every kind of preference is read with its default`() {
        val prefs = prefs(ConfigurableFakeSource(id = 77))
        prefs.keys shouldBe setOf("quality", "split", "locked", "domain", "langs")
        prefs.getValue("quality").currentValue shouldBe "high"
        prefs.getValue("split").currentValue shouldBe false
        prefs.getValue("domain").currentValue shouldBe "ejemplo.com"
        prefs.getValue("langs").currentValue shouldBe setOf("es")
    }

    @Test
    fun `an accepted change is saved where the extension reads it and the listener sees it`() {
        val source = ConfigurableFakeSource(id = 78)
        SourcePreferenceModel.set(prefs(source).getValue("quality"), "low") shouldBe true
        source.sourcePreferences().getString("quality", null) shouldBe "low"
        source.lastQuality shouldBe "low"
        SourcePreferenceModel.summaryOf(prefs(source).getValue("quality")) shouldBe "Baja"
    }

    @Test
    fun `a change the extension's listener rejects is not saved`() {
        val source = ConfigurableFakeSource(id = 79)
        SourcePreferenceModel.set(prefs(source).getValue("locked"), true) shouldBe false
        source.sourcePreferences().getBoolean("locked", false) shouldBe false
    }
}

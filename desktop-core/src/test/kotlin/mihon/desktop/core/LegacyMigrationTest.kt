package mihon.desktop.core

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.io.RandomAccessFile
import java.net.URI
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.io.path.readText
import kotlin.io.path.writeText

class LegacyMigrationTest {
    private val root = createTempDirectory("José Pérez migración")
    private val roaming = root.resolve("Roaming").createDirectories()
    private val local = root.resolve("Local").createDirectories()
    private val documents = root.resolve("OneDrive").resolve("Documentos").createDirectories()
    private val env = mapOf("APPDATA" to roaming.toString(), "LOCALAPPDATA" to local.toString())

    private fun oldSettings(storageDir: String?) {
        val settings = roaming.resolve("MangaReader/settings").createDirectories()
        val properties = Properties().apply {
            setProperty("library_update_interval", "12")
            if (storageDir != null) setProperty("__APP_STATE_storage_dir", storageDir)
        }
        settings.resolve("app.mangareader_preferences.xml").outputStream().use { properties.storeToXML(it, null) }
    }

    private fun newSettings(): Properties = Properties().apply {
        roaming.resolve("Turboyomi/settings/app.turboyomi_preferences.xml").inputStream().use { loadFromXML(it) }
    }

    private fun storedStorage() = Path.of(URI(newSettings().getProperty("__APP_STATE_storage_dir")))

    @Test
    fun `MangaReader's folders and settings move to Turboyomi, and a default storage folder follows`() {
        oldSettings(storageDir = documents.resolve("MangaReader").toUri().toString())
        roaming.resolve("MangaReader/db").createDirectories().resolve("tachiyomi.db").writeText("db")
        local.resolve("MangaReader/cef").createDirectories().resolve("x").writeText("cef")
        documents.resolve("MangaReader/downloads").createDirectories().resolve("c.cbz").writeText("cbz")

        LegacyMigration.run(env) { documents } shouldBe LegacyMigration.Result.Migrated

        roaming.resolve("Turboyomi/db/tachiyomi.db").readText() shouldBe "db"
        local.resolve("Turboyomi/cef/x").readText() shouldBe "cef"
        documents.resolve("Turboyomi/downloads/c.cbz").readText() shouldBe "cbz"
        roaming.resolve("MangaReader").exists() shouldBe false
        roaming.resolve("Turboyomi/settings/app.mangareader_preferences.xml").exists() shouldBe false
        newSettings().getProperty("library_update_interval") shouldBe "12"
        storedStorage() shouldBe documents.resolve("Turboyomi")
    }

    @Test
    fun `a storage folder of the user's choosing stays where it is`() {
        val elsewhere = root.resolve("D Mangas").createDirectories()
        oldSettings(storageDir = elsewhere.toUri().toString())
        documents.resolve("MangaReader").createDirectories()

        LegacyMigration.run(env) { documents } shouldBe LegacyMigration.Result.Migrated

        storedStorage() shouldBe elsewhere
        documents.resolve("MangaReader").exists() shouldBe true
        documents.resolve("Turboyomi").exists() shouldBe false
    }

    @Test
    fun `a storage dir written in another uri form is still recognized as the default`() {
        // file:/C:/... as File.toURI writes it, against file:///C:/... from Path.toUri
        oldSettings(storageDir = documents.resolve("MangaReader").toFile().toURI().toString())
        documents.resolve("MangaReader").createDirectories()

        LegacyMigration.run(env) { documents } shouldBe LegacyMigration.Result.Migrated

        documents.resolve("Turboyomi").exists() shouldBe true
        storedStorage() shouldBe documents.resolve("Turboyomi")
    }

    @Test
    fun `nothing moves once Turboyomi has its own folder`() {
        oldSettings(storageDir = null)
        roaming.resolve("Turboyomi").createDirectories()

        LegacyMigration.run(env) { documents } shouldBe LegacyMigration.Result.NotNeeded

        roaming.resolve("MangaReader").exists() shouldBe true
    }

    @Test
    fun `a folder in use puts everything back as it was`() {
        oldSettings(storageDir = null)
        val db = local.resolve("MangaReader/db").createDirectories().resolve("open.db").apply { writeText("x") }
        // RandomAccessFile opens without FILE_SHARE_DELETE, so Windows refuses to move the folder holding it
        RandomAccessFile(db.toFile(), "rw").use {
            LegacyMigration.run(env) { documents }.shouldBeInstanceOf<LegacyMigration.Result.Failed>()
        }

        roaming.resolve("MangaReader/settings/app.mangareader_preferences.xml").exists() shouldBe true
        roaming.resolve("Turboyomi").exists() shouldBe false
        local.resolve("MangaReader/db/open.db").exists() shouldBe true
    }

    @Test
    fun `the development home skips the migration`() {
        oldSettings(storageDir = null)

        LegacyMigration.run(env + ("TURBOYOMI_HOME" to root.resolve("dev").toString())) { documents } shouldBe
            LegacyMigration.Result.NotNeeded
    }
}

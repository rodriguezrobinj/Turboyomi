package mihon.desktop.app.backup

import eu.kanade.presentation.more.settings.screen.data.formatFileSize
import eu.kanade.tachiyomi.data.export.LibraryExporter
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import kotlin.io.path.createTempDirectory

class LibraryExporterTest {
    @Test
    fun `the CSV has a BOM for Excel and only the chosen columns`(): Unit = runBlocking {
        val file = createTempDirectory("Exportar José").toFile().resolve("biblioteca.csv")
        val favorites = listOf(
            Manga.create().copy(title = "Ñandú, el \"ave\"", author = "José Pérez", artist = "Ana"),
            Manga.create().copy(title = "Sin autor", author = null, artist = "Luis"),
        )

        LibraryExporter.exportToCsv(
            file,
            favorites,
            LibraryExporter.ExportOptions(includeTitle = true, includeAuthor = true, includeArtist = false),
        )

        val bytes = file.readBytes()
        bytes.take(3).map { it.toInt() and 0xff } shouldBe listOf(0xef, 0xbb, 0xbf)
        bytes.drop(3).toByteArray().decodeToString() shouldBe "\"Ñandú, el \"\"ave\"\"\",José Pérez\r\nSin autor,"
    }

    @Test
    fun `sizes read like Android's, in 1000-based units`() {
        formatFileSize(512) shouldBe "512 B"
        formatFileSize(1_500_000_000) shouldEndWith "GB"
    }
}

package eu.kanade.tachiyomi.data.export

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.domain.manga.model.Manga
import java.io.File

object LibraryExporter {

    data class ExportOptions(
        val includeTitle: Boolean,
        val includeAuthor: Boolean,
        val includeArtist: Boolean,
    )

    // desktop: a local file, with a UTF-8 BOM so Excel on Windows doesn't read it as ANSI (spec 5a §3 G)
    suspend fun exportToCsv(
        file: File,
        favorites: List<Manga>,
        options: ExportOptions,
    ) {
        withContext(Dispatchers.IO) {
            file.outputStream().use { outputStream ->
                outputStream.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                outputStream.write(generateCsvData(favorites, options).toByteArray())
            }
        }
    }

    private val escapeRequired = listOf("\r", "\n", "\"", ",")

    private fun generateCsvData(favorites: List<Manga>, options: ExportOptions): String {
        val columnSize = listOf(
            options.includeTitle,
            options.includeAuthor,
            options.includeArtist,
        )
            .count { it }

        val rows = buildList(favorites.size) {
            favorites.forEach { manga ->
                buildList(columnSize) {
                    if (options.includeTitle) add(manga.title)
                    if (options.includeAuthor) add(manga.author)
                    if (options.includeArtist) add(manga.artist)
                }
                    .let(::add)
            }
        }
        return rows.joinToString("\r\n") { columns ->
            columns.joinToString(",") columns@{ column ->
                if (column.isNullOrBlank()) return@columns ""
                if (escapeRequired.any { column.contains(it) }) {
                    column.replace("\"", "\"\"").let { "\"$it\"" }
                } else {
                    column
                }
            }
        }
    }
}

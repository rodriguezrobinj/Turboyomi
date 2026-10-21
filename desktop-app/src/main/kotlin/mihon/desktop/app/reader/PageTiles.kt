package mihon.desktop.app.reader

/** Taller than this, a page is decoded in strips: one texture that tall can exceed the GPU's limit. */
const val TILE_LIMIT = 8192

/** Height of each strip of a page taller than [TILE_LIMIT]. */
const val TILE_HEIGHT = 4096

/** The rows to decode a page of [height] in (spec 2a §4, "Imágenes"): all of them, or strips of at most 4096 px. */
fun tileRanges(height: Int): List<IntRange> =
    if (height <= TILE_LIMIT) {
        listOf(0 until height)
    } else {
        (0 until height step TILE_HEIGHT).map { top -> top until minOf(top + TILE_HEIGHT, height) }
    }

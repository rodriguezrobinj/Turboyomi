package mihon.desktop.app.reader

/** One screen of the paged viewer: one page or two side by side, as indices into the chapter's pages. */
sealed interface Spread {
    val pages: List<Int>

    /** One page, or one [half] of a wide one split in two (spec 8c). */
    data class Single(val page: Int, val half: Half? = null) : Spread {
        override val pages get() = listOf(page)
    }

    data class Double(val first: Int, val second: Int) : Spread {
        override val pages get() = listOf(first, second)
    }
}

/**
 * Pairs [count] pages two by two in reading order (spec 2a §4, "Doble página"). A page wider than tall (a double-page
 * scan) shows alone and pairing restarts after it. [shifted] ("desplazar 1") shows the first page alone, which moves
 * every pair by one when the chapter's spreads start on the wrong page.
 */
fun pairPages(count: Int, isWide: (Int) -> Boolean, shifted: Boolean): List<Spread> {
    val spreads = mutableListOf<Spread>()
    var i = 0
    if (shifted && count > 0) {
        spreads += Spread.Single(0)
        i = 1
    }
    while (i < count) {
        if (isWide(i) || i + 1 >= count || isWide(i + 1)) {
            spreads += Spread.Single(i)
            i++
        } else {
            spreads += Spread.Double(i, i + 1)
            i += 2
        }
    }
    return spreads
}

/** Left to right on screen: in right-to-left reading the first page of a pair goes on the right. */
fun Spread.inDisplayOrder(rightToLeft: Boolean): List<Int> = if (rightToLeft) pages.reversed() else pages

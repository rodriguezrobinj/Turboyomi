package mihon.desktop.app.reader

/** Which half of a wide (double-page) scan a split spread shows (spec 8c §3 B). */
enum class Half { Left, Right }

/** The halves of a wide page in reading order: left first from left to right, right first from right to left. */
fun readingHalves(rightToLeft: Boolean, invert: Boolean): List<Half> =
    if (rightToLeft != invert) listOf(Half.Right, Half.Left) else listOf(Half.Left, Half.Right)

/** One spread per page, two for a wide one: one per half, in [halves]' order. */
fun splitPages(count: Int, isWide: (Int) -> Boolean, halves: List<Half>): List<Spread> =
    (0 until count).flatMap { page ->
        if (isWide(page)) halves.map { Spread.Single(page, it) } else listOf(Spread.Single(page))
    }

/** Degrees to turn a [width] × [height] page so a wide one fills the screen: 90, −90 when [invert], 0 for others. */
fun wideRotation(width: Int, height: Int, rotate: Boolean, invert: Boolean): Int = when {
    !rotate || width <= height -> 0
    invert -> -90
    else -> 90
}

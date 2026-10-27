package mihon.desktop.app.reader.image

import kotlin.math.abs

/** Pixels [left, right) × [top, bottom) of a page. */
data class CropRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

/**
 * The page without its margins (spec 8c §3 A): rows and columns from each edge that are all the corner's color, when
 * that color is nearly white or nearly black. A page left with less than a quarter of its area (a blank page, a tiny
 * mark) and a colored border (a cover) stay whole. Rows are checked every [SAMPLE] pixels across.
 */
fun contentBounds(width: Int, height: Int, pixel: (x: Int, y: Int) -> Int): CropRect {
    val whole = CropRect(0, 0, width, height)
    val border = pixel(0, 0)
    if (!border.isNearWhite() && !border.isNearBlack()) return whole
    fun blankRow(y: Int) = (0 until width step SAMPLE).all { pixel(it, y).isCloseTo(border) }
    fun blankColumn(
        x: Int,
        top: Int,
        bottom: Int,
    ) = (top until bottom step SAMPLE).all { pixel(x, it).isCloseTo(border) }

    var top = 0
    while (top < height && blankRow(top)) top++
    if (top == height) return whole
    var bottom = height
    while (bottom > top && blankRow(bottom - 1)) bottom--
    var left = 0
    while (left < width && blankColumn(left, top, bottom)) left++
    var right = width
    while (right > left && blankColumn(right - 1, top, bottom)) right--

    val area = (right - left).toLong() * (bottom - top)
    return if (area * 4 < width.toLong() * height) whole else CropRect(left, top, right, bottom)
}

private const val SAMPLE = 4
private const val TOLERANCE = 25

private fun Int.channels() = intArrayOf(this shr 16 and 0xFF, this shr 8 and 0xFF, this and 0xFF)

private fun Int.isNearWhite() = channels().all { it >= 255 - TOLERANCE }

private fun Int.isNearBlack() = channels().all { it <= TOLERANCE }

private fun Int.isCloseTo(other: Int): Boolean {
    val a = channels()
    val b = other.channels()
    return a.indices.all { abs(a[it] - b[it]) <= TOLERANCE }
}

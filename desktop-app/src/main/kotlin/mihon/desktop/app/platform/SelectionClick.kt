package mihon.desktop.app.platform

/** What a click on a list item does (spec 3a §3 A): Mihon's click, plus shift+click for a range while selecting. */
enum class SelectionClick { Open, Toggle, Range }

fun selectionClick(shift: Boolean, selecting: Boolean): SelectionClick = when {
    !selecting -> SelectionClick.Open
    shift -> SelectionClick.Range
    else -> SelectionClick.Toggle
}

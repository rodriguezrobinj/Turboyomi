package mihon.desktop.app.main

/**
 * The screens going back took off the navigator's stack, for the mouse's forward button and Alt+Right (spec 12 §2),
 * like a browser's forward history: opening any other screen empties it.
 */
class ForwardHistory<T> {
    private val screens = ArrayDeque<T>()
    private var previous: List<T> = emptyList()

    /** Call with the stack after every change. */
    fun onStackChanged(stack: List<T>) {
        when {
            // Back: the screens it took off, the nearest one on top
            stack.size < previous.size && previous.subList(0, stack.size) == stack ->
                previous.subList(stack.size, previous.size).asReversed().forEach(screens::addLast)
            // Forward: the screen it put back
            stack.size == previous.size + 1 &&
                stack.subList(0, previous.size) == previous &&
                stack.last() == screens.lastOrNull() -> screens.removeLast()
            stack != previous -> screens.clear()
        }
        previous = stack.toList()
    }

    /** The screen forward puts back, or null. */
    fun next(): T? = screens.lastOrNull()
}

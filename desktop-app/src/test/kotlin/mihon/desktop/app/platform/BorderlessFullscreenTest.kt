package mihon.desktop.app.platform

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Test
import java.awt.GraphicsEnvironment
import java.awt.Insets
import javax.swing.JFrame
import javax.swing.SwingUtilities

class BorderlessFullscreenTest {
    @Test
    fun `a decorated frame covers its whole monitor with no title bar, then gets both back`() {
        assumeFalse(GraphicsEnvironment.isHeadless())
        val frame = onEdt {
            JFrame("test").apply {
                setBounds(100, 100, 800, 600)
                isVisible = true
            }
        }
        try {
            val before = onEdt { frame.bounds }
            val saved = onEdt { BorderlessFullscreen.enter(frame) }
            eventually(frame) {
                frame.bounds == frame.graphicsConfiguration.bounds && frame.insets == Insets(0, 0, 0, 0)
            }

            onEdt { BorderlessFullscreen.exit(frame, saved) }
            eventually(frame) { frame.bounds == before && frame.insets.top > 0 }
        } finally {
            onEdt { frame.dispose() }
        }
    }

    private fun <T> onEdt(block: () -> T): T {
        var result: Result<T>? = null
        SwingUtilities.invokeAndWait { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    // The frame learns its new bounds and insets from window messages, a moment after the call
    private fun eventually(frame: JFrame, check: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!onEdt(check)) {
            if (System.currentTimeMillis() > deadline) {
                onEdt { "bounds ${frame.bounds}, insets ${frame.insets}" } shouldBe "the expected ones"
            }
            Thread.sleep(50)
        }
    }
}

package mihon.desktop.app.platform

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.io.path.Path

class ExplorerTest {
    @Test
    fun `a file opens selected in its folder, its path one argument whatever its spaces`() {
        Explorer.selectCommand(Path("C:\\Mangas\\José Pérez\\Capítulo 1.cbz")) shouldBe
            listOf("explorer.exe", "/select,", "C:\\Mangas\\José Pérez\\Capítulo 1.cbz")
    }
}

package mihon.desktop.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class ComposeSmokeTest {
    @Test
    fun `compose desktop renders material3 text`() = runComposeUiTest {
        setContent { MaterialTheme { Text("José Pérez") } }
        onNodeWithText("José Pérez").assertExists()
    }
}

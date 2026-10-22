package mihon.core.designsystem.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// desktop: Android reads the device configuration; on desktop the window width decides, as in isTabletUi
@Composable
private fun windowWidth(): Dp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }

@Composable
fun isMediumWidthWindow(): Boolean = windowWidth() > MediumWidthWindowSize

@Composable
fun isExpandedWidthWindow(): Boolean = windowWidth() > ExpandedWidthWindowSize

val MediumWidthWindowSize = 600.dp
val ExpandedWidthWindowSize = 840.dp

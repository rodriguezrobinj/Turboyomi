package eu.kanade.presentation.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

// desktop: Android reads the device configuration; on desktop the window width decides (two panes from 840dp)
@Composable
fun isTabletUi(): Boolean {
    val info = LocalWindowInfo.current
    return with(LocalDensity.current) { info.containerSize.width.toDp() } >= 840.dp
}

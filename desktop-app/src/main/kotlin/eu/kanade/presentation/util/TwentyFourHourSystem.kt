package eu.kanade.presentation.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.text.DateFormat
import java.text.SimpleDateFormat

// desktop: Android asks the system setting; here the locale's short time pattern tells if it has AM/PM
@Composable
fun rememberIs24HourSystem(): Boolean = remember {
    (DateFormat.getTimeInstance(DateFormat.SHORT) as? SimpleDateFormat)?.toPattern()?.contains('a') != true
}

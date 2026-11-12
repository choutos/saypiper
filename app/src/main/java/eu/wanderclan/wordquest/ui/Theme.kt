package eu.wanderclan.wordquest.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val colours = lightColorScheme(
    primary = Color(0xFF3B5BA9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE3FF),
    onPrimaryContainer = Color(0xFF0E1B48),
    secondary = Color(0xFFB86E00),
    secondaryContainer = Color(0xFFFFE3C0),
    onSecondaryContainer = Color(0xFF3B2200),
    tertiary = Color(0xFF2E7D4F),
    tertiaryContainer = Color(0xFFC8F0D5),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
    background = Color(0xFFFBF8F2),
    surface = Color(0xFFFBF8F2),
)

/** Star colour on the path and the result screen. */
val StarGold = Color(0xFFF2A93B)
val StarEmpty = Color(0xFFD5D0C6)

@Composable
fun WordQuestTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colours, content = content)
}

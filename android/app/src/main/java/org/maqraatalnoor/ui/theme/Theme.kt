package org.maqraatalnoor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun typographyFor(family: FontFamily): Typography = Typography(
    headlineLarge = TextStyle(family, FontWeight.Bold, 26.sp),
    headlineMedium = TextStyle(family, FontWeight.Bold, 22.sp),
    titleLarge = TextStyle(family, FontWeight.Bold, 19.sp),
    titleMedium = TextStyle(family, FontWeight.Bold, 17.sp),
    bodyLarge = TextStyle(family, FontWeight.Normal, 16.sp),
    bodyMedium = TextStyle(family, FontWeight.Normal, 15.sp),
    labelLarge = TextStyle(family, FontWeight.Bold, 15.sp),
    labelMedium = TextStyle(family, FontWeight.Medium, 14.sp),
    bodySmall = TextStyle(family, FontWeight.Normal, 13.sp),
)

private val MaqraatalNoorColors = lightColorScheme(
    primary = MaqraatalNoorGreen,
    onPrimary = Paper,
    secondary = Gold,
    onSecondary = DeepGreenDark,
    background = MaqraatalNoorBackground,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Cream,
    onSurfaceVariant = Muted,
    error = LiveRed,
    outline = CardLine,
)

/**
 * [uiFamily] is the bundled Plex family by default, or a user-imported
 * font family resolved by the caller. The Quran family is never replaced
 * by this parameter (see [QuranFamily]).
 */
@Composable
fun MaqraatalNoorTheme(
    uiFamily: FontFamily = PlexFamily,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = MaqraatalNoorColors,
        typography = typographyFor(uiFamily),
        content = content,
    )
}

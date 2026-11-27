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
    headlineLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 26.sp),
    headlineMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 19.sp),
    titleMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 17.sp),
    bodyLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    labelLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 15.sp),
    labelMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 13.sp),
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
    error = FeedbackError,
    outline = CardLine,
)

/**
 * [uiFamily] is a user-imported family when the user selected one; null means
 * the bundled default, Thmanyah Sans, with [PlexFamily] as the safe fallback.
 * The Quran family is never replaced by this parameter (see [QuranFamily]).
 */
@Composable
fun MaqraatalNoorTheme(
    uiFamily: FontFamily? = null,
    content: @Composable () -> Unit,
) {
    val defaultFamily = rememberDefaultUiFamily()
    MaterialTheme(
        colorScheme = MaqraatalNoorColors,
        typography = typographyFor(uiFamily ?: defaultFamily),
        content = content,
    )
}

package org.maqraatalnoor.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.maqraatalnoor.R
import org.maqraatalnoor.fonts.ThmanyahSans

/**
 * Fallback interface family: bundled IBM Plex Sans Arabic (open licence).
 * The app's default is Thmanyah Sans (see [rememberDefaultUiFamily]); Plex is
 * used only when the encrypted Thmanyah payload cannot be loaded. A
 * user-imported font replaces either family at runtime on the user's device.
 */
val PlexFamily = FontFamily(
    Font(R.font.ibm_plex_sans_arabic_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_arabic_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_arabic_bold, FontWeight.Bold),
)

/**
 * Resolves the default interface family from the encrypted, in-memory-only
 * Thmanyah Sans payload, falling back to [PlexFamily] on any error. The
 * decrypt/build work is cached by [ThmanyahSans] and remembered here, so it
 * runs once per composition rather than once per text style.
 */
@Composable
fun rememberDefaultUiFamily(): FontFamily {
    val context = LocalContext.current.applicationContext
    return remember(context) { ThmanyahSans.load(context) } ?: PlexFamily
}

/**
 * Quran text: Amiri Quran (SIL OFL) for the Madani page layout, with the
 * bundled Noto Naskh as the second face. Never the interface font and never
 * Thamaniya — the Uthmani page keeps its Naskh drawing.
 */
/**
 * Quran text: bundled Noto Naskh Arabic (OFL) for the live-strip verse and any
 * inline Quran text. The mushaf screens themselves render the printed pages
 * as images, so no Quran face is shipped for them. Never the interface font
 * and never Thamaniya.
 */
val QuranFamily = FontFamily(
    Font(R.font.noto_naskh_arabic_regular, FontWeight.Normal),
    Font(R.font.noto_naskh_arabic_bold, FontWeight.Bold),
)

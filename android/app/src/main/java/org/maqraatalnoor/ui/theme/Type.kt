package org.maqraatalnoor.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.maqraatalnoor.R

/**
 * Interface type: bundled IBM Plex Sans Arabic (open licence), the
 * documented stand-in for Thamaniya in design v1.1. No Thamaniya file is
 * bundled. A user-imported font (see fonts/FontLoader) replaces this
 * family at runtime on the user's own device only.
 */
val PlexFamily = FontFamily(
    Font(R.font.ibm_plex_sans_arabic_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_arabic_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_arabic_semi_bold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_arabic_bold, FontWeight.Bold),
)

/**
 * Quran text: bundled Noto Naskh Arabic (OFL). Never the interface font
 * and never Thamaniya — the Uthmani page keeps its Naskh drawing.
 */
val QuranFamily = FontFamily(
    Font(R.font.noto_naskh_arabic_regular, FontWeight.Normal),
    Font(R.font.noto_naskh_arabic_bold, FontWeight.Bold),
)

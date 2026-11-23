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
    Font(R.font.ibm_plex_sans_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_arabic_bold, FontWeight.Bold),
)

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

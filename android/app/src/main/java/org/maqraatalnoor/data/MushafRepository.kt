package org.maqraatalnoor.data

/**
 * Mushaf engine (sample only).
 *
 * This build ships ONE page — Surah Al-Fatiha — as the working sample, with
 * the exact Uthmani text used in design screen 05 (render.py). Numbering
 * follows the design/Tanzil convention used in that screen: the Basmala is
 * counted as ayah 1 of Al-Fatiha. The full 604-page Mushaf is NOT bundled:
 * it needs an openly licensed text source (Tanzil) and a later import pass
 * (see REPORT.md and docs/BACKEND-NOTES.md). Do not paste a scanned or
 * copyrighted Mushaf into this project.
 */
object MushafRepository {

    private val fatiha = MushafPage(
        number = 1,
        surah = "الفاتحة",
        ayahs = listOf(
            Ayah(1, "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ"),
            Ayah(2, "الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ"),
            Ayah(3, "الرَّحْمَٰنِ الرَّحِيمِ"),
            Ayah(4, "مَالِكِ يَوْمِ الدِّينِ"),
            Ayah(5, "إِيَّاكَ نَعْبُدُ وَإِيَّاكَ نَسْتَعِينُ"),
            Ayah(6, "اهْدِنَا الصِّرَاطَ الْمُسْتَقِيمَ"),
            Ayah(7, "صِرَاطَ الَّذِينَ أَنْعَمْتَ عَلَيْهِمْ غَيْرِ الْمَغْضُوبِ عَلَيْهِمْ وَلَا الضَّالِّينَ"),
        ),
    )

    val pages: List<MushafPage> = listOf(fatiha)

    /** Total pages available in this build (1 = sample only, not 604). */
    val pageCount: Int get() = pages.size

    fun page(number: Int): MushafPage? = pages.firstOrNull { it.number == number }

    fun firstPage(): MushafPage = fatiha
}

package org.maqraatalnoor.domain

import kotlin.math.roundToInt

/** Pure Mushaf navigation / highlight rules. No Android types. */
object MushafLogic {

    /** Clamp a requested page into the pages actually available in this build. */
    fun clampPage(requested: Int, totalPages: Int): Int =
        requested.coerceIn(1, maxOf(1, totalPages))

    fun hasPrevious(page: Int): Boolean = page > 1

    fun hasNext(page: Int, totalPages: Int): Boolean = page < totalPages

    fun isHighlighted(ayahNumber: Int, highlightedAyah: Int): Boolean =
        ayahNumber == highlightedAyah && highlightedAyah > 0

    /** Reading progress as 0..1, safe for a zero total. */
    fun readingProgress(pagesRead: Int, totalPages: Int): Float {
        if (totalPages <= 0) return 0f
        return (pagesRead.coerceIn(0, totalPages).toFloat() / totalPages).coerceIn(0f, 1f)
    }

    /** Percent label used on class cards, e.g. 68. */
    fun progressPercent(progress: Float): Int =
        (progress.coerceIn(0f, 1f) * 100).roundToInt()
}

package org.maqraatalnoor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maqraatalnoor.data.Level
import org.maqraatalnoor.domain.MushafLogic

class LevelProgressTest {

    @Test
    fun levelOrder_foundationFirstIjazaLast() {
        val ordered = Level.entries.sortedBy { it.order }
        assertEquals(Level.FOUNDATION, ordered.first())
        assertEquals(Level.IJAZA, ordered.last())
        assertEquals(4, ordered.size)
    }

    @Test
    fun unlocked_requiresPreviousLevelsCompleted() {
        val completed = setOf(Level.FOUNDATION)
        assertTrue(Level.unlocked(Level.FOUNDATION, completed))
        assertTrue(Level.unlocked(Level.TAJWEED, completed))
        assertFalse(Level.unlocked(Level.HIFZ, completed))
        assertFalse(Level.unlocked(Level.IJAZA, completed))
    }

    @Test
    fun unlocked_ijazaNeedsHifzAndTajweed() {
        val completed = setOf(Level.FOUNDATION, Level.TAJWEED, Level.HIFZ)
        assertTrue(Level.unlocked(Level.IJAZA, completed))
    }

    @Test
    fun unlocked_nothingCompletedOnlyFoundation() {
        assertTrue(Level.unlocked(Level.FOUNDATION, emptySet()))
        assertFalse(Level.unlocked(Level.TAJWEED, emptySet()))
    }

    @Test
    fun studentLevelProgress_percentLabel() {
        assertEquals(68, MushafLogic.progressPercent(0.68f))
        assertEquals(100, MushafLogic.progressPercent(1f))
    }

    @Test
    fun arabicNames_presentForAllLevels() {
        assertEquals("التأسيس", Level.FOUNDATION.arabicName)
        assertEquals("التجويد", Level.TAJWEED.arabicName)
        assertEquals("الحفظ", Level.HIFZ.arabicName)
        assertEquals("الإجازة", Level.IJAZA.arabicName)
    }
}

package org.maqraatalnoor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maqraatalnoor.domain.MushafLogic

class MushafLogicTest {

    @Test
    fun clampPage_keepsThePageInsideTheMushaf() {
        assertEquals(1, MushafLogic.clampPage(0, 604))
        assertEquals(604, MushafLogic.clampPage(999, 604))
        assertEquals(300, MushafLogic.clampPage(300, 604))
        assertEquals(1, MushafLogic.clampPage(7, 1))
    }

    @Test
    fun hasPreviousAndNext_acrossTheWholeMushaf() {
        assertFalse(MushafLogic.hasPrevious(1))
        assertTrue(MushafLogic.hasPrevious(2))
        assertTrue(MushafLogic.hasNext(1, 604))
        assertFalse(MushafLogic.hasNext(604, 604))
    }

    @Test
    fun isHighlighted_onlyExactAyah() {
        assertTrue(MushafLogic.isHighlighted(3, 3))
        assertFalse(MushafLogic.isHighlighted(2, 3))
        assertFalse(MushafLogic.isHighlighted(0, 0))
    }

    @Test
    fun readingProgress_clampsAndHandlesZeroTotal() {
        assertEquals(0f, MushafLogic.readingProgress(5, 0), 0.0001f)
        assertEquals(0.5f, MushafLogic.readingProgress(1, 2), 0.0001f)
        assertEquals(1f, MushafLogic.readingProgress(99, 2), 0.0001f)
        assertEquals(0f, MushafLogic.readingProgress(-3, 10), 0.0001f)
    }

    @Test
    fun progressPercent_roundsToWholePercent() {
        assertEquals(68, MushafLogic.progressPercent(0.68f))
        assertEquals(0, MushafLogic.progressPercent(-0.5f))
        assertEquals(100, MushafLogic.progressPercent(1.5f))
    }
}

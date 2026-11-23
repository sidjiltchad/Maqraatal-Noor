package org.maqraatalnoor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maqraatalnoor.data.MushafRepository
import org.maqraatalnoor.domain.MushafLogic

class MushafLogicTest {

    @Test
    fun clampPage_staysWithinAvailablePages() {
        assertEquals(1, MushafLogic.clampPage(0, 1))
        assertEquals(1, MushafLogic.clampPage(99, 1))
        assertEquals(1, MushafLogic.clampPage(1, 1))
    }

    @Test
    fun hasPreviousAndNext_singleSamplePage() {
        assertFalse(MushafLogic.hasPrevious(1))
        assertFalse(MushafLogic.hasNext(1, MushafRepository.pageCount))
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

    @Test
    fun sampleMushaf_isFatihaOnlySevenAyahs() {
        // This build ships the sample only: 1 page, never 604.
        assertEquals(1, MushafRepository.pageCount)
        val page = MushafRepository.page(1)
        assertNotNull(page)
        assertEquals("الفاتحة", page!!.surah)
        assertEquals(7, page.ayahs.size)
        assertEquals(1, page.ayahs.first().number)
        assertTrue(page.ayahs.first().text.startsWith("بِسْمِ"))
        assertEquals(7, page.ayahs.last().number)
        assertNull(MushafRepository.page(2))
        assertNull(MushafRepository.page(604))
    }
}

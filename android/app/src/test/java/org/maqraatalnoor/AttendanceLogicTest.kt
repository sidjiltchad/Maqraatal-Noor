package org.maqraatalnoor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maqraatalnoor.data.AttendanceRecord
import org.maqraatalnoor.domain.AttendanceLogic

class AttendanceLogicTest {

    @Test
    fun summary_countsPresentAndAbsent() {
        val records = listOf(
            AttendanceRecord("s1", "h1", true),
            AttendanceRecord("s2", "h1", true),
            AttendanceRecord("s3", "h1", false),
        )
        val summary = AttendanceLogic.summary(records)
        assertEquals(2, summary.present)
        assertEquals(1, summary.absent)
        assertEquals(3, summary.total)
    }

    @Test
    fun mark_replacesPreviousRecordForSameHalqa() {
        val records = listOf(AttendanceRecord("s1", "h1", true))
        val updated = AttendanceLogic.mark(records, "s1", "h1", false)
        assertEquals(1, updated.size)
        assertFalse(AttendanceLogic.isPresent(updated, "s1", "h1"))
    }

    @Test
    fun mark_keepsOtherHalqasUntouched() {
        val records = listOf(AttendanceRecord("s1", "h1", true), AttendanceRecord("s1", "h2", true))
        val updated = AttendanceLogic.mark(records, "s1", "h1", false)
        assertTrue(AttendanceLogic.isPresent(updated, "s1", "h2"))
        assertEquals(2, updated.size)
    }

    @Test
    fun isPresent_falseWhenNoRecord() {
        assertFalse(AttendanceLogic.isPresent(emptyList(), "s9", "h1"))
    }

    @Test
    fun summary_emptyIsZero() {
        val summary = AttendanceLogic.summary(emptyList())
        assertEquals(0, summary.present)
        assertEquals(0, summary.absent)
        assertEquals(0, summary.total)
    }
}

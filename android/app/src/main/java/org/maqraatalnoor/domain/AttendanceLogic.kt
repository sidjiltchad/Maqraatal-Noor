package org.maqraatalnoor.domain

import org.maqraatalnoor.data.AttendanceRecord

/** Pure attendance rules. No Android types. */
object AttendanceLogic {

    data class Summary(val present: Int, val absent: Int, val total: Int)

    fun summary(records: List<AttendanceRecord>): Summary {
        val present = records.count { it.present }
        return Summary(present = present, absent = records.size - present, total = records.size)
    }

    /** Mark (or re-mark) one student; re-marking replaces the old record. */
    fun mark(
        records: List<AttendanceRecord>,
        studentId: String,
        halqaId: String,
        present: Boolean,
    ): List<AttendanceRecord> {
        val kept = records.filterNot { it.studentId == studentId && it.halqaId == halqaId }
        return kept + AttendanceRecord(studentId, halqaId, present)
    }

    fun isPresent(records: List<AttendanceRecord>, studentId: String, halqaId: String): Boolean =
        records.firstOrNull { it.studentId == studentId && it.halqaId == halqaId }?.present ?: false
}

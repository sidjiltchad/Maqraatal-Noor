package org.maqraatalnoor.data

/** Roles in Maqraatal-Noor. Only invited accounts exist; there is no public signup. */
enum class Role { STUDENT, TEACHER, SUPERVISOR }

/** Learning levels, in order. Ijaza unlocks only after Tajweed + Hifz. */
enum class Level(val arabicName: String, val order: Int) {
    FOUNDATION("التأسيس", 0),
    TAJWEED("التجويد", 1),
    HIFZ("الحفظ", 2),
    IJAZA("الإجازة", 3);

    companion object {
        fun unlocked(level: Level, completed: Set<Level>): Boolean = when (level) {
            FOUNDATION -> true
            TAJWEED -> FOUNDATION in completed
            HIFZ -> TAJWEED in completed
            IJAZA -> TAJWEED in completed && HIFZ in completed
        }
    }
}

data class Teacher(
    val id: String,
    val name: String,
    val specialty: String,
)

data class Student(
    val id: String,
    val name: String,
    val level: Level,
    val levelProgress: Float,
) {
    /** First letter, used for the initial-circle avatar. No photos anywhere. */
    val initial: String get() = name.firstOrNull()?.toString() ?: "؟"
}

data class ClassGroup(
    val id: String,
    val name: String,
    val level: Level,
    val teacher: Teacher,
    val days: String,
    val time: String,
    val surah: String,
    val enrolled: Int,
    val capacity: Int,
    val progress: Float,
    val locked: Boolean = false,
    val isPrivate: Boolean = false,
)

data class Halqa(
    val id: String,
    val classId: String,
    val title: String,
    val surah: String,
    val page: Int,
    val time: String,
    val durationMinutes: Int,
    /** Teacher-controlled display line in the live strip (e.g. Al-Kahf 1). */
    val currentAyahText: String,
)

data class Evaluation(
    val studentId: String,
    val tajweedScore: Int, // 1..5
    val hifzScore: Int, // 1..5
    val present: Boolean,
    val note: String = "",
) {
    init {
        require(tajweedScore in 1..5) { "tajweedScore must be 1..5" }
        require(hifzScore in 1..5) { "hifzScore must be 1..5" }
    }
}

data class AttendanceRecord(
    val studentId: String,
    val halqaId: String,
    val present: Boolean,
)

enum class TurnState { WAITING, READING, DONE }

/** One recitation turn in the live queue. */
data class RecitationTurn(
    val student: Student,
    val state: TurnState = TurnState.WAITING,
    val handRaised: Boolean = false,
)

data class ChatMessage(
    val id: String,
    val isTeacher: Boolean,
    val text: String,
    val isVoiceNote: Boolean = false,
    val durationSeconds: Int = 0,
)

/** One surah row in a mushaf's index. */
data class MushafSurah(val index: Int, val name: String, val page: Int)

/** A hizb or juz mark: its number and the page it starts on. */
data class MushafMark(val number: Int, val page: Int)

/** One bundled mushaf: assets folder, page count, surah and hizb indexes. */
data class MushafSource(
    val id: String,
    val title: String,
    val assetDir: String,
    val pageCount: Int,
    val surahs: List<MushafSurah>,
    val hizbs: List<MushafMark> = emptyList(),
    val juzs: List<MushafMark> = emptyList(),
)
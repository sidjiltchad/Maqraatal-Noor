package org.maqraatalnoor.data

/**
 * Local demo data matching design v1.1 screens. No server, no accounts:
 * the signed-in student is Amina, the teacher is Umm Sara. Real accounts
 * and sync are backend work (see docs/BACKEND-NOTES.md).
 */
object DemoRepository {

    val teacherSara = Teacher("t-sara", "أم سارة", "التجويد")
    val teacherMariam = Teacher("t-mariam", "أم مريم", "الحفظ")
    val supervisor = Teacher("t-super", "المشرفة العامة", "الإجازة بالسند")

    val currentStudent = Student("s-amina", "أمينة", Level.TAJWEED, 0.68f)

    val students: List<Student> = listOf(
        Student("s-fatima", "فاطمة", Level.TAJWEED, 0.72f),
        Student("s-mariam", "مريم", Level.TAJWEED, 0.61f),
        Student("s-khadija", "خديجة", Level.TAJWEED, 0.55f),
        Student("s-noor", "نور", Level.HIFZ, 0.42f),
        Student("s-asia", "آسية", Level.TAJWEED, 0.48f),
        Student("s-hiba", "هبة", Level.FOUNDATION, 0.20f),
    )

    val classes: List<ClassGroup> = listOf(
        ClassGroup(
            id = "c-tajweed",
            name = "فصل التجويد — المتقدمات",
            level = Level.TAJWEED,
            teacher = teacherSara,
            days = "الأحد والثلاثاء",
            time = "17:00",
            surah = "سورة الكهف",
            enrolled = 14,
            capacity = 16,
            progress = 0.68f,
        ),
        ClassGroup(
            id = "c-hifz",
            name = "فصل الحفظ — جزء عم",
            level = Level.HIFZ,
            teacher = teacherMariam,
            days = "السبت",
            time = "10:00",
            surah = "سورة النبأ",
            enrolled = 11,
            capacity = 16,
            progress = 0.42f,
        ),
        ClassGroup(
            id = "c-private",
            name = "إقراء فردي",
            level = Level.TAJWEED,
            teacher = teacherSara,
            days = "الخميس",
            time = "18:30",
            surah = "سورة الكهف",
            enrolled = 1,
            capacity = 1,
            progress = 0.80f,
            isPrivate = true,
        ),
        ClassGroup(
            id = "c-ijaza",
            name = "مستوى الإجازة",
            level = Level.IJAZA,
            teacher = supervisor,
            days = "بالسند",
            time = "مع المشرفة العامة",
            surah = "يفتح بعد إتمام الحفظ والتجويد",
            enrolled = 0,
            capacity = 8,
            progress = 0f,
            locked = true,
        ),
    )

    val nextHalqa = Halqa(
        id = "h-today",
        classId = "c-tajweed",
        title = "الحلقة القادمة",
        surah = "سورة الكهف",
        page = 293,
        time = "اليوم • 17:00",
        durationMinutes = 60,
        currentAyahText = "الْحَمْدُ لِلَّهِ الَّذِي أَنزَلَ عَلَىٰ عَبْدِهِ الْكِتَابَ",
    )

    fun classById(id: String): ClassGroup? = classes.firstOrNull { it.id == id }

    fun studentsInClass(classId: String): List<Student> = when (classId) {
        "c-tajweed" -> students
        "c-hifz" -> students.take(4)
        else -> students.take(1)
    }

    fun initialQueue(): List<RecitationTurn> {
        val first = students.take(4)
        return first.mapIndexed { index, student ->
            RecitationTurn(
                student = student,
                state = if (index == 0) TurnState.READING else TurnState.WAITING,
                handRaised = index == 1,
            )
        }
    }

    val evaluations: Map<String, Evaluation> = mapOf(
        "s-fatima" to Evaluation("s-fatima", tajweedScore = 4, hifzScore = 5, present = true),
        "s-mariam" to Evaluation("s-mariam", tajweedScore = 4, hifzScore = 4, present = true),
        "s-khadija" to Evaluation("s-khadija", tajweedScore = 3, hifzScore = 4, present = true),
        "s-noor" to Evaluation("s-noor", tajweedScore = 4, hifzScore = 3, present = true),
        "s-asia" to Evaluation("s-asia", tajweedScore = 3, hifzScore = 3, present = true),
        "s-hiba" to Evaluation("s-hiba", tajweedScore = 2, hifzScore = 2, present = false),
    )

    val privateMessages: List<ChatMessage> = listOf(
        ChatMessage("m1", isTeacher = true, text = "أحسنتِ، أعيدي الآية مع مدّ البدل"),
        ChatMessage("m2", isTeacher = false, text = "تسميع مريم • تسجيل صوتي", isVoiceNote = true, durationSeconds = 42),
        ChatMessage("m3", isTeacher = true, text = "المدّ هنا حركتان فقط، لا ستّا"),
        ChatMessage("m4", isTeacher = false, text = "فهمت، سأعيدها الآن"),
        ChatMessage("m5", isTeacher = true, text = "تقييم الجلسة: التجويد 4/5 • أعيدي أول 5 آيات واجبا"),
    )

    /** Weekly progress shown on the home screen. */
    data class WeeklyProgress(val halqas: Int, val pagesRead: Int, val tajweedScore: Int)

    val weeklyProgress = WeeklyProgress(halqas = 3, pagesRead = 12, tajweedScore = 4)
}

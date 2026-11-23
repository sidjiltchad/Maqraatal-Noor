package org.maqraatalnoor.ui.navigation

/** Navigation destinations, one per design screen (plus settings). */
object Routes {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val CLASSES = "classes"
    const val LIVE = "live/{classId}"
    const val MUSHAF = "mushaf"
    const val TEACHER = "teacher/{classId}"
    const val PRIVATE = "private/{studentId}"
    const val SETTINGS = "settings"

    fun live(classId: String): String = "live/$classId"
    fun teacher(classId: String): String = "teacher/$classId"
    fun privateSession(studentId: String): String = "private/$studentId"
}

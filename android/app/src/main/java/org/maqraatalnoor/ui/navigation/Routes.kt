package org.maqraatalnoor.ui.navigation

/** Navigation destinations, one per design screen (plus settings). */
object Routes {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val LIVE = "live/{classId}"
    const val MUSHAF = "mushaf"
    const val TEACHER = "teacher/{classId}"
    const val SETTINGS = "settings"
    const val ACCOUNT = "account"
    const val OWNER_DASHBOARD = "owner-dashboard"
    const val GROUPS = "groups"
    const val ADMIN = "admin"
    const val CHATS = "chats"
    const val CALL = "call?withVideo={withVideo}&thread={thread}&studentId={studentId}"

    fun live(classId: String): String = "live/$classId"
    fun teacher(classId: String): String = "teacher/$classId"
    fun call(withVideo: Boolean = false, thread: String = "halqa", studentId: String? = null): String =
        "call?withVideo=$withVideo&thread=$thread&studentId=${studentId.orEmpty()}"
}

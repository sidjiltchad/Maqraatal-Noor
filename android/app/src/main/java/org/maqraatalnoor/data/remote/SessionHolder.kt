package org.maqraatalnoor.data.remote

/**
 * جلسة التطبيق الحالية كمرجع واحد تقرأه بقية الـViewModels،
 * فلا تُمرَّر عبر التنقل ولا تُخزَّن في عدة أماكن.
 */
object SessionHolder {

    @Volatile
    var session: SupabaseApi.AuthSession? = null
        private set

    @Volatile
    var profile: RemoteProfile? = null
        private set

    @Volatile
    var isStaff: Boolean = false
        private set

    @Volatile
    var userId: String = ""
        private set

    val remote: HalqaRemote? get() = session?.let { HalqaRemote(it) }

    fun update(session: SupabaseApi.AuthSession, profile: RemoteProfile) {
        this.session = session
        this.profile = profile
        this.userId = profile.id
        this.isStaff = profile.role == "teacher" || profile.role == "supervisor"
    }

    fun clear() {
        session = null
        profile = null
        isStaff = false
        userId = ""
    }
}

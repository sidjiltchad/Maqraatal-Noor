package org.maqraatalnoor.ui.viewmodels

import org.maqraatalnoor.data.remote.RemoteHalqa
import org.maqraatalnoor.data.remote.RemoteMember
import org.maqraatalnoor.data.remote.RemoteMembership
import org.maqraatalnoor.data.remote.RemoteProfile
import org.maqraatalnoor.data.remote.SupabaseApi

/**
 * حالة الدخول: تحميل، غير مسجّلة، أو مسجّلة ببياناتها الحقيقية من Supabase.
 *
 * [SignedIn.isOwner] comes only from the authenticated `public.is_owner()`
 * check. The ordinary profile role may make a user staff, but never the owner.
 */
sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState

    data class SignedIn(
        val session: SupabaseApi.AuthSession,
        val profile: RemoteProfile,
        val membership: RemoteMembership?,
        val halqa: RemoteHalqa?,
        val members: List<RemoteMember>,
        val isOwner: Boolean = false,
    ) : AuthState {
        val isStaff: Boolean get() = profile.role == "teacher" || profile.role == "supervisor"
        val students: List<RemoteMember> get() = members.filter { it.role == "student" }
        val teachers: List<RemoteMember> get() = members.filter { it.role != "student" }
        val teachersLabel: String
            get() = teachers.joinToString("، ") { it.name }.ifBlank { "لا معلمات بعد" }
    }
}

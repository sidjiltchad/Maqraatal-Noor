package org.maqraatalnoor.data.remote

import android.content.Context
import android.content.SharedPreferences

/** يحفظ جلسة الدخول على الجهاز (رمز الوصول والتحديث ومعرّف المستخدمة). */
class SessionStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("supabase_session", Context.MODE_PRIVATE)

    fun save(session: SupabaseApi.AuthSession) {
        prefs.edit()
            .putString(KEY_ACCESS, session.accessToken)
            .putString(KEY_REFRESH, session.refreshToken)
            .putString(KEY_USER, session.userId)
            .putString(KEY_EMAIL, session.email)
            .apply()
    }

    fun load(): SupabaseApi.AuthSession? {
        val access = prefs.getString(KEY_ACCESS, null) ?: return null
        val userId = prefs.getString(KEY_USER, null) ?: return null
        return SupabaseApi.AuthSession(
            accessToken = access,
            refreshToken = prefs.getString(KEY_REFRESH, "").orEmpty(),
            userId = userId,
            email = prefs.getString(KEY_EMAIL, "").orEmpty(),
        )
    }

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_USER = "user_id"
        const val KEY_EMAIL = "email"
    }
}

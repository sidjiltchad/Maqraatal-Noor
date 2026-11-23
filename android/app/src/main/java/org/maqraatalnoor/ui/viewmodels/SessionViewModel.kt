package org.maqraatalnoor.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maqraatalnoor.data.remote.HalqaRemote
import org.maqraatalnoor.data.remote.RemoteHalqa
import org.maqraatalnoor.data.remote.RemoteMember
import org.maqraatalnoor.data.remote.RemoteMembership
import org.maqraatalnoor.data.remote.RemoteProfile
import org.maqraatalnoor.data.remote.SessionStore
import org.maqraatalnoor.data.remote.SupabaseApi

/** حالة الدخول: تحميل، غير مسجّلة، أو مسجّلة ببياناتها الحقيقية من Supabase. */
sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState

    data class SignedIn(
        val session: SupabaseApi.AuthSession,
        val profile: RemoteProfile,
        val membership: RemoteMembership?,
        val halqa: RemoteHalqa?,
        val members: List<RemoteMember>,
    ) : AuthState {
        val isStaff: Boolean get() = profile.role == "teacher" || profile.role == "supervisor"
        val students: List<RemoteMember> get() = members.filter { it.role == "student" }
        val teachers: List<RemoteMember> get() = members.filter { it.role != "student" }
        val teachersLabel: String
            get() = teachers.joinToString("، ") { it.name }.ifBlank { "لا معلمات بعد" }
    }
}

class SessionViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SessionStore(application)

    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    init {
        val saved = store.load()
        if (saved == null) {
            _state.value = AuthState.SignedOut
        } else {
            viewModelScope.launch { restore(saved) }
        }
    }

    private suspend fun restore(saved: SupabaseApi.AuthSession) {
        val session = withContext(Dispatchers.IO) {
            runCatching { SupabaseApi.refresh(saved.refreshToken) }.getOrElse { saved }
        }
        if (session.accessToken.isBlank()) {
            _state.value = AuthState.SignedOut
            return
        }
        store.save(session)
        load(session)
    }

    fun signIn(email: String, password: String) {
        if (_busy.value) return
        _busy.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val session = withContext(Dispatchers.IO) { SupabaseApi.signIn(email, password) }
                store.save(session)
                load(session)
            } catch (exc: Exception) {
                _error.value = friendly(exc)
            } finally {
                _busy.value = false
            }
        }
    }

    fun signOut() {
        val current = (_state.value as? AuthState.SignedIn)?.session
        store.clear()
        _state.value = AuthState.SignedOut
        if (current != null) {
            viewModelScope.launch(Dispatchers.IO) { SupabaseApi.signOut(current.accessToken) }
        }
    }

    fun reload() {
        val current = (_state.value as? AuthState.SignedIn)?.session ?: return
        viewModelScope.launch { load(current) }
    }

    private suspend fun load(session: SupabaseApi.AuthSession) {
        _busy.value = true
        try {
            val remote = HalqaRemote(session)
            val profile = withContext(Dispatchers.IO) { remote.profile() }
            if (profile == null) {
                _error.value = "لا يوجد ملف شخصي لهذا الحساب"
                _state.value = AuthState.SignedOut
                return
            }
            val membership = withContext(Dispatchers.IO) { remote.membership() }
            val halqa = if (membership != null) withContext(Dispatchers.IO) { remote.halqa() } else null
            val members = if (membership != null) withContext(Dispatchers.IO) { remote.members() } else emptyList()
            _state.value = AuthState.SignedIn(session, profile, membership, halqa, members)
            if (membership == null) {
                _error.value = "حسابك ليس عضوا في الحلقة بعد؛ أدخل كود الدعوة"
            }
        } catch (exc: Exception) {
            _error.value = friendly(exc)
            _state.value = AuthState.SignedOut
        } finally {
            _busy.value = false
        }
    }

    /** استهلاك كود دعوة من داخل التطبيق ثم إعادة تحميل العضوية. */
    fun redeem(code: String) {
        val current = (_state.value as? AuthState.SignedIn)?.session ?: return
        if (code.isBlank()) return
        _busy.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    SupabaseApi.rpc(
                        "redeem_invitation",
                        org.json.JSONObject().put("p_code", code.trim().uppercase()),
                        current.accessToken,
                    )
                }
                load(current)
            } catch (exc: Exception) {
                _error.value = friendly(exc)
            } finally {
                _busy.value = false
            }
        }
    }

    private fun friendly(exc: Exception): String {
        val message = exc.message.orEmpty()
        return when {
            message.contains("Invalid login credentials", ignoreCase = true) ->
                "البريد أو كلمة المرور غير صحيحة"

            message.contains("Email not confirmed", ignoreCase = true) ->
                "البريد غير مؤكد"

            message.contains("الدعوة غير صالحة") -> "كود الدعوة غير صالح أو منتهٍ"
            message.contains("Unable to resolve host", ignoreCase = true) ->
                "تعذّر الاتصال بالشبكة"

            message.isBlank() -> "تعذّر إتمام الطلب"
            else -> message
        }
    }
}

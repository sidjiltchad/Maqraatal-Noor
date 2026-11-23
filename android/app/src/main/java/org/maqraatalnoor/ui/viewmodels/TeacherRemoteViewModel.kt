package org.maqraatalnoor.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maqraatalnoor.data.remote.RemoteMember
import org.maqraatalnoor.data.remote.RemoteSession
import org.maqraatalnoor.data.remote.RemoteTurn
import org.maqraatalnoor.data.remote.SessionHolder
import org.maqraatalnoor.data.remote.attendance
import org.maqraatalnoor.data.remote.turns

/** تحكم المعلمة على بيانات Supabase الحقيقية: الجلسة، الحضور، الدور، التقييم. */
class TeacherRemoteViewModel : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val session: RemoteSession? = null,
        val students: List<RemoteMember> = emptyList(),
        val attendance: Map<String, Boolean> = emptyMap(),
        val turns: List<RemoteTurn> = emptyList(),
        val message: String? = null,
        val error: String? = null,
    ) {
        val presentCount: Int get() = students.count { attendance[it.userId] != false }
        val absentCount: Int get() = students.size - presentCount
        val currentReader: RemoteTurn? get() = turns.firstOrNull { it.state == "reading" }
    }

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val remote = SessionHolder.remote
            if (remote == null) {
                _state.value = UiState(loading = false, error = "لا توجد جلسة دخول")
                return@launch
            }
            try {
                val members = withContext(Dispatchers.IO) { remote.members() }
                val halqa = withContext(Dispatchers.IO) { remote.halqa() }
                var session = withContext(Dispatchers.IO) { remote.latestSession() }
                if (session == null && halqa != null && SessionHolder.isStaff) {
                    session = withContext(Dispatchers.IO) { remote.createSession(halqa.id) }
                }
                val attendance = session?.let { withContext(Dispatchers.IO) { remote.attendance(it.id) } }
                    ?: emptyMap()
                val turns = session?.let { withContext(Dispatchers.IO) { remote.turns(it.id) } } ?: emptyList()
                _state.value = UiState(
                    loading = false,
                    session = session,
                    students = members.filter { it.role == "student" },
                    attendance = attendance,
                    turns = turns,
                )
            } catch (exc: Exception) {
                _state.value = UiState(loading = false, error = exc.message)
            }
        }
    }

    fun toggleAttendance(studentId: String) {
        val remote = SessionHolder.remote ?: return
        val sessionId = _state.value.session?.id ?: return
        val present = _state.value.attendance[studentId] != false
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { remote.markAttendance(sessionId, studentId, !present) }
                _state.value = _state.value.copy(
                    attendance = _state.value.attendance + (studentId to !present),
                    message = "حُدِّث الحضور",
                )
            } catch (exc: Exception) {
                _state.value = _state.value.copy(error = exc.message)
            }
        }
    }

    fun giveTurn(studentId: String) {
        val remote = SessionHolder.remote ?: return
        val sessionId = _state.value.session?.id ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { remote.giveTurn(sessionId, studentId) }
                val turns = withContext(Dispatchers.IO) { remote.turns(sessionId) }
                _state.value = _state.value.copy(turns = turns, message = "أُسند الدور")
            } catch (exc: Exception) {
                _state.value = _state.value.copy(error = exc.message)
            }
        }
    }

    fun saveEvaluation(studentId: String, tajweed: Int, hifz: Int, note: String = "") {
        val remote = SessionHolder.remote ?: return
        val sessionId = _state.value.session?.id ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { remote.saveEvaluation(sessionId, studentId, tajweed, hifz, note) }
                _state.value = _state.value.copy(message = "حُفظ التقييم")
            } catch (exc: Exception) {
                _state.value = _state.value.copy(error = exc.message)
            }
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null, error = null)
    }
}

package org.maqraatalnoor.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maqraatalnoor.data.remote.RealtimeClient
import org.maqraatalnoor.data.remote.RemoteMember
import org.maqraatalnoor.data.remote.RemoteSession
import org.maqraatalnoor.data.remote.RemoteTurn
import org.maqraatalnoor.data.remote.SessionHolder
import org.maqraatalnoor.data.remote.attendance
import org.maqraatalnoor.data.remote.raiseHand
import org.maqraatalnoor.data.remote.turns

/**
 * الحلقة الحية على بيانات Supabase: الجلسة، الطابور، الحضور، ورفع اليد.
 * تُحدَّث الحالة تلقائيا كل ثانيتين (مزامنة شبه لحظية) وتُغلق عند مغادرة الشاشة.
 */
class LiveSessionViewModel : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val session: RemoteSession? = null,
        val members: List<RemoteMember> = emptyList(),
        val turns: List<RemoteTurn> = emptyList(),
        val attendance: Map<String, Boolean> = emptyMap(),
        val error: String? = null,
        val notice: String? = null,
    ) {
        val isLive: Boolean get() = session?.status == "live"
        val students: List<RemoteMember> get() = members.filter { it.role == "student" }
        val teachers: List<RemoteMember> get() = members.filter { it.role != "student" }
        val currentTurn: RemoteTurn? get() = turns.firstOrNull { it.state == "reading" }
        val currentReader: RemoteMember?
            get() = currentTurn?.let { turn -> members.firstOrNull { it.userId == turn.studentId } }
        val queue: List<Pair<RemoteMember, RemoteTurn>>
            get() = turns.sortedBy { it.position }.mapNotNull { turn ->
                members.firstOrNull { it.userId == turn.studentId }?.let { it to turn }
            }
        val presentCount: Int get() = students.count { attendance[it.userId] != false }
    }

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()
    private var poller: Job? = null

    /** true إذا كانت المستخدمة الحالية معلمة/مشرفة. */
    val isStaff: Boolean get() = SessionHolder.isStaff
    val myId: String get() = SessionHolder.userId

    private var realtime: RealtimeClient? = null
    private var halqaId: String? = null

    fun start(halqaId: String) {
        if (poller?.isActive == true) return
        this.halqaId = halqaId
        val token = SessionHolder.session?.accessToken
        if (token != null) {
            realtime = RealtimeClient(
                accessToken = token,
                tables = listOf("sessions", "recitation_turns", "session_attendance", "messages"),
                onChange = { viewModelScope.launch { refresh() } },
            ).also { it.connect() }
        }
        // شبكة أمان: تحديث كامل كل 20 ثانية إن انقطع الاشتراك اللحظي.
        poller = viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(20_000)
            }
        }
    }

    fun stop() {
        poller?.cancel()
        poller = null
        realtime?.close()
        realtime = null
    }

    private suspend fun refresh() {
        val remote = SessionHolder.remote ?: run {
            _state.value = UiState(loading = false, error = "لا توجد جلسة دخول")
            return
        }
        runCatching {
            val members = withContext(Dispatchers.IO) { remote.members() }
            var session = withContext(Dispatchers.IO) { remote.latestSession(halqaId) }
            if (session == null && SessionHolder.isStaff) {
                val halqa = withContext(Dispatchers.IO) { remote.halqa() }
                if (halqa != null && (halqaId.isNullOrBlank() || halqa.id == halqaId)) {
                    session = withContext(Dispatchers.IO) { remote.createSession(halqa.id) }
                }
            }
            val turns = session?.let { withContext(Dispatchers.IO) { remote.turns(it.id) } } ?: emptyList()
            val attendance = session?.let { withContext(Dispatchers.IO) { remote.attendance(it.id) } } ?: emptyMap()
            _state.value = _state.value.copy(
                loading = false,
                session = session,
                members = members,
                turns = turns,
                attendance = attendance,
                error = null,
            )
        }.onFailure { exc ->
            _state.value = _state.value.copy(loading = false, error = exc.message)
        }
    }

    fun raiseHand() {
        val remote = SessionHolder.remote ?: return
        val sessionId = _state.value.session?.id ?: return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { remote.raiseHand(sessionId) }
                _state.value = _state.value.copy(notice = "رُفعت يدك، انتظري دورك")
                refresh()
            }.onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun giveTurn(studentId: String) {
        val remote = SessionHolder.remote ?: return
        val sessionId = _state.value.session?.id ?: return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { remote.giveTurn(sessionId, studentId) }
                _state.value = _state.value.copy(notice = "أُسند الدور")
                refresh()
            }.onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun clearNotice() {
        _state.value = _state.value.copy(notice = null, error = null)
    }
}

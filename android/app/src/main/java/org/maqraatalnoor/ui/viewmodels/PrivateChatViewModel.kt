package org.maqraatalnoor.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maqraatalnoor.data.remote.SessionHolder
import org.maqraatalnoor.data.remote.sendPrivateMessage

/** محادثة خاصة حقيقية بين معلمة وطالبة (جدول messages في Supabase). */
class PrivateChatViewModel : ViewModel() {

    data class UiMessage(val id: String, val body: String, val mine: Boolean)

    data class UiState(
        val loading: Boolean = true,
        val studentId: String = "",
        val studentName: String = "",
        val messages: List<UiMessage> = emptyList(),
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private var poller: Job? = null

    /** تحديث تلقائي كل 3 ثوانٍ ما دامت الشاشة مفتوحة. */
    fun startPolling(studentId: String) {
        if (poller?.isActive == true) return
        poller = viewModelScope.launch {
            while (isActive) {
                load(studentId, silent = true)
                delay(3_000)
            }
        }
    }

    fun stopPolling() {
        poller?.cancel()
        poller = null
    }

    fun load(studentId: String, silent: Boolean = false) {
        val remote = SessionHolder.remote ?: return
        viewModelScope.launch {
            if (!silent) _state.value = _state.value.copy(loading = true, studentId = studentId, error = null)
            try {
                val name = withContext(Dispatchers.IO) {
                    runCatching {
                        org.maqraatalnoor.data.remote.SupabaseApi.get(
                            "profiles?select=full_name&id=eq.$studentId",
                            SessionHolder.session?.accessToken.orEmpty(),
                        ).optJSONObject(0)?.optString("full_name").orEmpty()
                    }.getOrDefault("")
                }
                val rows = withContext(Dispatchers.IO) { remote.messages("private", studentId) }
                _state.value = UiState(
                    loading = false,
                    studentId = studentId,
                    studentName = name,
                    messages = rows.map { UiMessage(it.id, it.body, it.isMine) },
                )
            } catch (exc: Exception) {
                _state.value = _state.value.copy(loading = false, error = exc.message)
            }
        }
    }

    fun send(text: String) {
        val remote = SessionHolder.remote ?: return
        val studentId = _state.value.studentId
        if (studentId.isBlank()) return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { remote.sendPrivateMessage(studentId, text) }
                load(studentId)
            } catch (exc: Exception) {
                _state.value = _state.value.copy(error = exc.message)
            }
        }
    }
}

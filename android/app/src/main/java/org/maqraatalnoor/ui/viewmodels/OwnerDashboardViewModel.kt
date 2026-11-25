package org.maqraatalnoor.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.maqraatalnoor.data.remote.SessionHolder
import org.maqraatalnoor.data.remote.SupabaseApi
import org.maqraatalnoor.domain.SupervisorAccountPolicy

/** Creation status for the owner-only General Supervisor form. */
sealed interface SupervisorCreationState {
    data object Idle : SupervisorCreationState
    data object Submitting : SupervisorCreationState
    data class Success(val fullName: String) : SupervisorCreationState
    data class Failed(val message: String) : SupervisorCreationState
}

/**
 * Calls the authenticated Edge Function with the caller's own JWT.
 *
 * The password is a method parameter only: it is not held in a StateFlow, not
 * persisted, not logged, and not stored on disk. The screen clears its local
 * field as soon as [SupervisorCreationState.Success] is observed.
 */
class OwnerDashboardViewModel : ViewModel() {

    private val _state = MutableStateFlow<SupervisorCreationState>(SupervisorCreationState.Idle)
    val state = _state.asStateFlow()

    fun createSupervisor(fullName: String, email: String, password: String) {
        if (_state.value is SupervisorCreationState.Submitting) return

        val session = SessionHolder.session
        if (session == null || session.accessToken.isBlank()) {
            _state.value = SupervisorCreationState.Failed(
                "انتهت الجلسة. سجّلي الدخول من جديد ثم أعيدي المحاولة.",
            )
            return
        }

        val payload = JSONObject()
            .put("fullName", SupervisorAccountPolicy.sanitizeName(fullName))
            .put("email", SupervisorAccountPolicy.normalizeEmail(email))
            .put("password", password)

        _state.value = SupervisorCreationState.Submitting
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    SupabaseApi.invokeFunction(
                        name = "create-general-supervisor",
                        payload = payload,
                        token = session.accessToken,
                    )
                }
                _state.value = SupervisorCreationState.Success(
                    SupervisorAccountPolicy.sanitizeName(fullName),
                )
            } catch (exc: CancellationException) {
                throw exc
            } catch (exc: Exception) {
                val status = (exc as? SupabaseApi.ApiException)?.status
                _state.value = SupervisorCreationState.Failed(
                    SupervisorAccountPolicy.friendlyCreationError(status, exc.message.orEmpty()),
                )
            }
        }
    }

    /** Clears a success/error banner when the owner edits the form again. */
    fun clearMessage() {
        if (_state.value !is SupervisorCreationState.Submitting) {
            _state.value = SupervisorCreationState.Idle
        }
    }
}

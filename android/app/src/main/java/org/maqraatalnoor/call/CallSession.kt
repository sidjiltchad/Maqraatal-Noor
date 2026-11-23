package org.maqraatalnoor.call

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Call-session contract. This is a state machine only: it models join,
 * mute, camera, hand-raising, turn-giving, mute-all and locking exactly as
 * the UI needs. It carries NO audio or video: there is no WebRTC, no
 * signalling and no server in this build. A real implementation (SFU +
 * signalling + accounts) plugs in behind this same interface; see
 * docs/BACKEND-NOTES.md. Nothing here must be read as a real call.
 */
data class Participant(
    val id: String,
    val name: String,
    val initial: String,
    val isTeacher: Boolean,
    val muted: Boolean = true,
    val cameraOn: Boolean = false,
    val handRaised: Boolean = false,
)

data class CallState(
    val joined: Boolean = false,
    val locked: Boolean = false,
    val participants: List<Participant> = emptyList(),
    val currentReaderId: String? = null,
    val selfMuted: Boolean = true,
    val selfCameraOn: Boolean = false,
    val handRaised: Boolean = false,
)

interface CallSession {
    val state: StateFlow<CallState>
    fun join()
    fun leave()
    fun toggleMute()
    fun toggleCamera()
    fun raiseHand()
    fun lowerHand()
    fun giveTurn(studentId: String)
    fun muteAll()
    fun setLocked(locked: Boolean)
}

/**
 * Local simulation of [CallSession]: state changes only, on this device.
 * Joining while locked is refused; the teacher is never muted by mute-all.
 */
class LocalCallSession(
    private val selfId: String,
    initialParticipants: List<Participant>,
) : CallSession {

    private val _state = MutableStateFlow(
        CallState(participants = initialParticipants),
    )
    override val state: StateFlow<CallState> = _state.asStateFlow()

    override fun join() {
        _state.update { current ->
            if (current.locked && !current.joined) current else current.copy(joined = true)
        }
    }

    override fun leave() {
        _state.update {
            it.copy(
                joined = false,
                selfMuted = true,
                selfCameraOn = false,
                handRaised = false,
                currentReaderId = null,
            )
        }
    }

    override fun toggleMute() {
        _state.update { it.copy(selfMuted = !it.selfMuted) }
    }

    override fun toggleCamera() {
        _state.update { it.copy(selfCameraOn = !it.selfCameraOn) }
    }

    override fun raiseHand() {
        _state.update {
            it.copy(
                handRaised = true,
                participants = it.participants.map { p ->
                    if (p.id == selfId) p.copy(handRaised = true) else p
                },
            )
        }
    }

    override fun lowerHand() {
        _state.update {
            it.copy(
                handRaised = false,
                participants = it.participants.map { p ->
                    if (p.id == selfId) p.copy(handRaised = false) else p
                },
            )
        }
    }

    override fun giveTurn(studentId: String) {
        _state.update { current ->
            if (current.participants.none { it.id == studentId }) {
                current
            } else {
                current.copy(
                    currentReaderId = studentId,
                    participants = current.participants.map { p ->
                        if (p.id == studentId) p.copy(handRaised = false, muted = false) else p
                    },
                )
            }
        }
    }

    override fun muteAll() {
        _state.update { current ->
            current.copy(
                participants = current.participants.map { p ->
                    if (p.isTeacher) p else p.copy(muted = true)
                },
            )
        }
    }

    override fun setLocked(locked: Boolean) {
        _state.update { it.copy(locked = locked) }
    }
}

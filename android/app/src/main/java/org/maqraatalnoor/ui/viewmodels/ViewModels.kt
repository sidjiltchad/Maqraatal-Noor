package org.maqraatalnoor.ui.viewmodels

import android.app.Application
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.maqraatalnoor.call.CallState
import org.maqraatalnoor.call.LocalCallSession
import org.maqraatalnoor.call.Participant
import org.maqraatalnoor.data.AttendanceRecord
import org.maqraatalnoor.data.ChatMessage
import org.maqraatalnoor.data.DemoRepository
import org.maqraatalnoor.data.Evaluation
import org.maqraatalnoor.data.MushafRepository
import org.maqraatalnoor.data.MushafSource
import org.maqraatalnoor.data.SettingsStore
import org.maqraatalnoor.data.Student
import org.maqraatalnoor.domain.AttendanceLogic
import org.maqraatalnoor.domain.MushafLogic
import org.maqraatalnoor.domain.QueueLogic
import org.maqraatalnoor.fonts.FontLoader

/** Home screen state for the signed-in student (local demo data). */
class HomeViewModel : ViewModel() {
    val student: Student = DemoRepository.currentStudent
    val halqa = DemoRepository.nextHalqa
    val weekly = DemoRepository.weeklyProgress
    val classes = DemoRepository.classes
}

/** Classes and levels screen. */
class ClassesViewModel : ViewModel() {
    val student: Student = DemoRepository.currentStudent
    val classes = DemoRepository.classes
}

/** Live halqa: local call session + recitation queue. No real audio/video. */
class LiveHalqaViewModel : ViewModel() {

    private val session = LocalCallSession(
        selfId = DemoRepository.currentStudent.id,
        initialParticipants = listOf(
            Participant("t-sara", "أم سارة", "س", isTeacher = true, muted = false),
        ) + DemoRepository.students.take(4).map {
            Participant(it.id, it.name, it.initial, isTeacher = false)
        },
    )

    val callState: StateFlow<CallState> = session.state

    private val _turns = MutableStateFlow(DemoRepository.initialQueue())
    val turns = _turns.asStateFlow()

    private val _isTeacherView = MutableStateFlow(false)
    val isTeacherView = _isTeacherView.asStateFlow()

    private val _timerSeconds = MutableStateFlow(24 * 60 + 10)
    val timerSeconds = _timerSeconds.asStateFlow()

    fun join() = session.join()
    fun leave() = session.leave()
    fun toggleMute() = session.toggleMute()
    fun toggleCamera() = session.toggleCamera()
    fun setTeacherView(enabled: Boolean) { _isTeacherView.value = enabled }

    fun raiseHand() {
        session.raiseHand()
        val selfId = DemoRepository.currentStudent.id
        _turns.update { QueueLogic.raiseHand(it, selfId) }
    }

    fun lowerHandForSelf() {
        session.lowerHand()
        val selfId = DemoRepository.currentStudent.id
        _turns.update { QueueLogic.lowerHand(it, selfId) }
    }

    fun giveTurn(studentId: String) {
        session.giveTurn(studentId)
        _turns.update { QueueLogic.giveTurn(it, studentId) }
    }

    fun muteAll() = session.muteAll()
    fun setLocked(locked: Boolean) = session.setLocked(locked)
}

/** Mushaf reader: two bundled mushafs (reading / tajweed), page images. */
class MushafViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MushafRepository(application.assets)

    val sources: List<MushafSource> = repository.sources()

    private val _source = MutableStateFlow(
        sources.firstOrNull() ?: MushafSource("none", "مصحف", "", 1, emptyList()),
    )
    val source = _source.asStateFlow()

    private val _page = MutableStateFlow(_source.value.surahs.firstOrNull()?.page ?: 1)
    val page = _page.asStateFlow()

    fun pagePath(number: Int): String = repository.pagePath(_source.value, number)

    fun selectSource(id: String) {
        sources.firstOrNull { it.id == id }?.let { chosen ->
            _source.value = chosen
            _page.value = chosen.surahs.firstOrNull()?.page ?: 1
        }
    }

    fun goToPage(number: Int) {
        _page.value = MushafLogic.clampPage(number, _source.value.pageCount)
    }
}
/** Teacher control: attendance, turn-giving, quick evaluation. */
class TeacherViewModel : ViewModel() {

    private val halqaId = DemoRepository.nextHalqa.id

    private val _records = MutableStateFlow<List<AttendanceRecord>>(
        DemoRepository.students.map {
            AttendanceRecord(it.id, halqaId, present = DemoRepository.evaluations[it.id]?.present ?: true)
        },
    )
    val records = _records.asStateFlow()

    private val _evaluations = MutableStateFlow(DemoRepository.evaluations)
    val evaluations = _evaluations.asStateFlow()

    private val _turns = MutableStateFlow(DemoRepository.initialQueue())
    val turns = _turns.asStateFlow()

    private val _savedMessage = MutableStateFlow<String?>(null)
    val savedMessage = _savedMessage.asStateFlow()

    fun summary() = AttendanceLogic.summary(_records.value)

    fun toggleAttendance(studentId: String) {
        val current = AttendanceLogic.isPresent(_records.value, studentId, halqaId)
        _records.update { AttendanceLogic.mark(it, studentId, halqaId, !current) }
    }

    fun giveTurn(studentId: String) {
        _turns.update { QueueLogic.giveTurn(it, studentId) }
    }

    fun saveEvaluation(studentId: String, evaluation: Evaluation) {
        _evaluations.update { it + (studentId to evaluation) }
        _savedMessage.value = "حُفظ التقييم وأُرسل للطالبة"
    }

    fun clearSavedMessage() {
        _savedMessage.value = null
    }
}

/** Private 1-to-1 session: local messages only, teacher and student only. */
class PrivateSessionViewModel : ViewModel() {

    private val _messages = MutableStateFlow(DemoRepository.privateMessages)
    val messages = _messages.asStateFlow()

    private var counter = 100

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        counter += 1
        _messages.update {
            it + ChatMessage("m$counter", isTeacher = false, text = trimmed)
        }
    }

    /** Voice-note playback is not implemented in this build; the bubble is a placeholder. */
    fun playVoiceNote(message: ChatMessage) {
        // Intentionally no-op: no audio is recorded or played locally yet.
    }
}

/**
 * Settings / font screen. Font import is device-local only (see FontLoader).
 * The selected font replaces the interface family; Quran text is untouched.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = SettingsStore(application)
    private val context = application.applicationContext

    private val _imported = MutableStateFlow(FontLoader.listImported(context))
    val imported = _imported.asStateFlow()

    val selectedFontId: StateFlow<String> = settings.selectedFontId.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsStore.FontLoaderDefaultId,
    )

    val followTeacher: StateFlow<Boolean> = settings.followTeacher.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), true,
    )

    /** Resolved interface family: imported Typeface, or the bundled Plex family. */
    val uiFontFamily: StateFlow<FontFamily?> = selectedFontId.map { fontId ->
        FontLoader.typefaceFor(context, fontId)?.let { typeface: Typeface ->
            FontFamily(typeface)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refreshImported() {
        _imported.value = FontLoader.listImported(context)
    }

    fun importFont(uri: Uri, displayName: String): Boolean {
        val importedFont = FontLoader.importFont(context, uri, displayName) ?: return false
        refreshImported()
        viewModelScope.launch { settings.setSelectedFontId(importedFont.id) }
        return true
    }

    fun selectFont(fontId: String) {
        viewModelScope.launch { settings.setSelectedFontId(fontId) }
    }

    fun deleteFont(fontId: String) {
        if (FontLoader.deleteFont(context, fontId)) {
            refreshImported()
            viewModelScope.launch { settings.setSelectedFontId(SettingsStore.FontLoaderDefaultId) }
        }
    }

    fun setFollowTeacher(enabled: Boolean) {
        viewModelScope.launch { settings.setFollowTeacher(enabled) }
    }
}

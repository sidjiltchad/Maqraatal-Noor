package org.maqraatalnoor.ui.viewmodels

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import io.livekit.android.room.participant.RemoteParticipant
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.maqraatalnoor.BuildConfig
import org.maqraatalnoor.data.remote.SessionHolder
import java.util.concurrent.TimeUnit

/**
 * غرفة LiveKit الحقيقية: يجلب توكن الغرفة من دالة الحافة (لا أسرار في التطبيق)،
 * يتصل، يفعّل الميكروفون، ويُحدِّث المشاركات وحالة الاتصال.
 */
class CallViewModel(application: Application) : AndroidViewModel(application) {

    data class VideoFeed(val name: String, val track: VideoTrack, val isLocal: Boolean)

    enum class Phase { IDLE, CONNECTING, CONNECTED, FAILED, CLOSED }

    data class UiState(
        val phase: Phase = Phase.IDLE,
        val room: String = "maqraa-main",
        val participants: List<String> = emptyList(),
        val videoFeeds: List<VideoFeed> = emptyList(),
        val liveRoom: Room? = null,
        val micEnabled: Boolean = true,
        val cameraEnabled: Boolean = false,
        val error: String? = null,
        val isStaff: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private var room: Room? = null
    private val http = OkHttpClient.Builder().readTimeout(45, TimeUnit.SECONDS).build()

    fun join(startCamera: Boolean = false, thread: String = "halqa", studentId: String = "") {
        if (_state.value.phase == Phase.CONNECTING || _state.value.phase == Phase.CONNECTED) return
        viewModelScope.launch {
            _state.value = _state.value.copy(phase = Phase.CONNECTING, error = null)
            try {
                val token = withContext(Dispatchers.IO) { fetchToken(thread, studentId) }
                val instance = LiveKit.create(appContext = getApplication())
                room = instance
                withContext(Dispatchers.IO) { instance.connect(token.url, token.token) }
                instance.localParticipant.setMicrophoneEnabled(true)
                if (startCamera) instance.localParticipant.setCameraEnabled(true)
                _state.value = _state.value.copy(
                    phase = Phase.CONNECTED,
                    room = token.room,
                    liveRoom = instance,
                    micEnabled = true,
                    cameraEnabled = startCamera,
                    isStaff = token.role == "teacher" || token.role == "supervisor",
                )
                observe(instance)
            } catch (exc: Exception) {
            _state.value = _state.value.copy(phase = Phase.FAILED, cameraEnabled = false, error = friendly(exc))
                runCatching { room?.disconnect() }
            }
        }
    }

    private suspend fun observe(instance: Room) {
        viewModelScope.launch {
            while (isActive) {
                val names = buildList {
                    instance.localParticipant.name?.let { add("$it (أنت)") }
                    instance.remoteParticipants.values.forEach { participant: RemoteParticipant ->
                        add(participant.name ?: participant.identity?.value ?: "مشاركة")
                    }
                }
                val feeds = buildList {
                    val local = instance.localParticipant
                    (local.getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack)?.let {
                        add(VideoFeed(local.name ?: "أنت", it, true))
                    }
                    instance.remoteParticipants.values.forEach { participant ->
                        (participant.getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack)?.let {
                            add(VideoFeed(participant.name ?: participant.identity?.value ?: "مشاركة", it, false))
                        }
                    }
                }
                _state.value = _state.value.copy(participants = names, videoFeeds = feeds)
                delay(1_500)
            }
        }
    }

    fun toggleMic() {
        val instance = room ?: return
        viewModelScope.launch {
            val next = !_state.value.micEnabled
            runCatching { instance.localParticipant.setMicrophoneEnabled(next) }
                .onSuccess { _state.value = _state.value.copy(micEnabled = next) }
                .onFailure { _state.value = _state.value.copy(error = friendly(it)) }
        }
    }

    fun toggleCamera() {
        val instance = room ?: return
        viewModelScope.launch {
            val next = !_state.value.cameraEnabled
            runCatching { instance.localParticipant.setCameraEnabled(next) }
                .onSuccess { _state.value = _state.value.copy(cameraEnabled = next) }
                .onFailure { _state.value = _state.value.copy(error = friendly(it)) }
        }
    }

    fun leave() {
        val instance = room ?: return
        viewModelScope.launch {
            runCatching { instance.disconnect() }
            room = null
            _state.value = _state.value.copy(phase = Phase.CLOSED, participants = emptyList(), videoFeeds = emptyList(), liveRoom = null, cameraEnabled = false)
        }
    }

    override fun onCleared() {
        runCatching { room?.disconnect() }
        room = null
        super.onCleared()
    }

    private data class TokenBundle(val url: String, val token: String, val room: String, val role: String)

    private fun fetchToken(thread: String, studentId: String): TokenBundle {
        val session = SessionHolder.session ?: error("لا توجد جلسة دخول")
        if (thread == "private" && studentId.isBlank()) error("تعذّر تحديد الطالبة للمكالمة الخاصة")
        val payload = JSONObject()
            .put("thread", thread)
            .put("student_id", studentId.takeIf { thread == "private" })
            .toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${BuildConfig.SUPABASE_URL.trimEnd('/')}/functions/v1/livekit-token")
            .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer ${session.accessToken}")
            .post(payload)
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    JSONObject(body).optString("error")
                }.getOrNull().orEmpty()
                error(
                    when {
                        message.contains("not_a_member") -> "حسابك ليس عضوا في الحلقة"
                        message.contains("server_not_configured") -> "الخادم غير مهيأ للمكالمات"
                        message.contains("unauthorized") -> "انتهت الجلسة، سجّلي الدخول من جديد"
                        else -> "تعذّر الحصول على توكن الغرفة"
                    },
                )
            }
            val json = JSONObject(body)
            return TokenBundle(
                url = json.getString("url"),
                token = json.getString("token"),
                room = json.optString("room", "maqraa-main"),
                role = json.optString("role", "student"),
            )
        }
    }

    private fun friendly(exc: Throwable): String = when {
        exc.message?.contains("Permission", ignoreCase = true) == true -> "لم يُسمح باستخدام الميكروفون"
        exc.message?.contains("Network", ignoreCase = true) == true -> "تعذّر الاتصال بالشبكة"
        exc.message.isNullOrBlank() -> "تعذّر إتمام الاتصال"
        else -> exc.message!!
    }
}

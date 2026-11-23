package org.maqraatalnoor.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class RemoteProfile(val id: String, val name: String, val role: String)

data class RemoteMembership(
    val userId: String,
    val name: String,
    val role: String,
    val level: String,
    val progress: Float,
)

data class RemoteHalqa(
    val id: String,
    val name: String,
    val days: String,
    val time: String,
    val capacity: Int,
)

data class RemoteMember(
    val userId: String,
    val name: String,
    val role: String,
    val level: String,
    val progress: Float,
)

data class RemoteSession(
    val id: String,
    val status: String,
    val page: Int,
    val readerId: String?,
)

data class RemoteMessage(
    val id: String,
    val isMine: Boolean,
    val senderName: String,
    val body: String,
    val isVoice: Boolean,
)

/**
 * وصول حقيقي إلى جداول الحلقة عبر PostgREST/RPC، بجلسة المستخدمة نفسها،
 * فكل العزل (RLS) مطبَّق من قاعدة البيانات لا من التطبيق.
 */
class HalqaRemote(internal val session: SupabaseApi.AuthSession) {

    internal val token get() = session.accessToken
    val userId: String get() = session.userId

    suspend fun profile(): RemoteProfile? = withContext(Dispatchers.IO) {
        val rows = SupabaseApi.get("profiles?select=id,full_name,role&id=eq.${session.userId}", token)
        val row = rows.optJSONObject(0) ?: return@withContext null
        RemoteProfile(
            id = row.getString("id"),
            name = row.optString("full_name", "عضوة"),
            role = row.optString("role", "student"),
        )
    }

    suspend fun membership(): RemoteMembership? = withContext(Dispatchers.IO) {
        val rows = SupabaseApi.get(
            "halqa_members?select=user_id,role,level,level_progress&user_id=eq.${session.userId}",
            token,
        )
        val row = rows.optJSONObject(0) ?: return@withContext null
        RemoteMembership(
            userId = row.getString("user_id"),
            name = "",
            role = row.optString("role", "student"),
            level = row.optString("level", "foundation"),
            progress = row.optDouble("level_progress", 0.0).toFloat(),
        )
    }

    suspend fun halqa(): RemoteHalqa? = withContext(Dispatchers.IO) {
        val rows = SupabaseApi.get("halqa?select=id,name,days,start_time,capacity&limit=1", token)
        val row = rows.optJSONObject(0) ?: return@withContext null
        val days = row.optJSONArray("days")?.let { array ->
            (0 until array.length()).map { array.optString(it) }.joinToString(" و")
        }.orEmpty()
        RemoteHalqa(
            id = row.getString("id"),
            name = row.optString("name", "الحلقة"),
            days = days,
            time = row.optString("start_time", "").take(5),
            capacity = row.optInt("capacity", 30),
        )
    }

    suspend fun members(): List<RemoteMember> = withContext(Dispatchers.IO) {
        val rows = SupabaseApi.get(
            "halqa_members?select=user_id,role,level,level_progress,status,profiles(full_name)" +
                "&status=eq.active&order=role.desc",
            token,
        )
        buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val name = row.optJSONObject("profiles")?.optString("full_name").orEmpty()
                add(
                    RemoteMember(
                        userId = row.getString("user_id"),
                        name = name.ifBlank { "عضوة" },
                        role = row.optString("role", "student"),
                        level = row.optString("level", "foundation"),
                        progress = row.optDouble("level_progress", 0.0).toFloat(),
                    ),
                )
            }
        }
    }

    suspend fun latestSession(): RemoteSession? = withContext(Dispatchers.IO) {
        val rows = SupabaseApi.get(
            "sessions?select=id,status,current_page,current_reader&order=created_at.desc&limit=1",
            token,
        )
        val row = rows.optJSONObject(0) ?: return@withContext null
        RemoteSession(
            id = row.getString("id"),
            status = row.optString("status", "scheduled"),
            page = row.optInt("current_page", 1),
            readerId = row.optString("current_reader").takeIf { it.isNotBlank() && it != "null" },
        )
    }

    suspend fun createSession(halqaId: String): RemoteSession = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("halqa_id", halqaId)
            .put("status", "live")
            .put("current_page", 5)
        val rows = SupabaseApi.post("sessions", payload, token)
        val row = rows.optJSONObject(0) ?: throw SupabaseApi.ApiException(500, "لم تُنشأ الجلسة")
        RemoteSession(
            id = row.getString("id"),
            status = row.optString("status", "live"),
            page = row.optInt("current_page", 1),
            readerId = null,
        )
    }

    suspend fun giveTurn(sessionId: String, studentId: String) = withContext(Dispatchers.IO) {
        SupabaseApi.rpc("give_turn", JSONObject().put("p_session", sessionId).put("p_student", studentId), token)
    }

    suspend fun markAttendance(sessionId: String, userId: String, present: Boolean) =
        withContext(Dispatchers.IO) {
            SupabaseApi.rpc(
                "mark_attendance",
                JSONObject()
                    .put("p_session", sessionId)
                    .put("p_user", userId)
                    .put("p_status", if (present) "present" else "absent"),
                token,
            )
        }

    suspend fun saveEvaluation(sessionId: String, studentId: String, tajweed: Int, hifz: Int, note: String) =
        withContext(Dispatchers.IO) {
            SupabaseApi.rpc(
                "save_evaluation",
                JSONObject()
                    .put("p_session", sessionId)
                    .put("p_student", studentId)
                    .put("p_tajweed", tajweed)
                    .put("p_hifz", hifz)
                    .put("p_note", note),
                token,
            )
        }

    suspend fun messages(thread: String, studentId: String? = null): List<RemoteMessage> =
        withContext(Dispatchers.IO) {
            val filter = if (thread == "private") {
                "&thread=eq.private&student_id=eq.$studentId"
            } else {
                "&thread=eq.halqa"
            }
            val rows = SupabaseApi.get(
                "messages?select=id,sender_id,body,kind&order=created_at.asc$filter",
                token,
            )
            buildList {
                for (index in 0 until rows.length()) {
                    val row = rows.optJSONObject(index) ?: continue
                    add(
                        RemoteMessage(
                            id = row.getString("id"),
                            isMine = row.optString("sender_id") == session.userId,
                            senderName = "",
                            body = row.optString("body"),
                            isVoice = row.optString("kind") == "voice",
                        ),
                    )
                }
            }
        }

    suspend fun sendHalqaMessage(body: String) = withContext(Dispatchers.IO) {
        SupabaseApi.rpc(
            "send_message",
            JSONObject().put("p_thread", "halqa").put("p_body", body).put("p_kind", "text"),
            token,
        )
    }
}

data class RemoteTurn(
    val studentId: String,
    val position: Int,
    val state: String,
    val handRaised: Boolean,
)

/** حضور الجلسة: معرّف العضوة ← حاضرة؟ */
suspend fun HalqaRemote.attendance(sessionId: String): Map<String, Boolean> =
    withContext(Dispatchers.IO) {
        val rows = SupabaseApi.get(
            "session_attendance?select=user_id,status&session_id=eq.$sessionId",
            session.accessToken,
        )
        buildMap {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                put(row.getString("user_id"), row.optString("status") == "present")
            }
        }
    }

suspend fun HalqaRemote.turns(sessionId: String): List<RemoteTurn> =
    withContext(Dispatchers.IO) {
        val rows = SupabaseApi.get(
            "recitation_turns?select=student_id,position,state,hand_raised" +
                "&session_id=eq.$sessionId&order=position.asc",
            session.accessToken,
        )
        buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                add(
                    RemoteTurn(
                        studentId = row.getString("student_id"),
                        position = row.optInt("position", index + 1),
                        state = row.optString("state", "waiting"),
                        handRaised = row.optBoolean("hand_raised", false),
                    ),
                )
            }
        }
    }

suspend fun HalqaRemote.raiseHand(sessionId: String) = withContext(Dispatchers.IO) {
    SupabaseApi.rpc("raise_hand", JSONObject().put("p_session", sessionId), session.accessToken).let { }
}

suspend fun HalqaRemote.sendPrivateMessage(studentId: String, body: String) =
    withContext(Dispatchers.IO) {
        SupabaseApi.rpc(
            "send_message",
            JSONObject()
                .put("p_thread", "private")
                .put("p_body", body)
                .put("p_student", studentId)
                .put("p_kind", "text"),
            session.accessToken,
        ).let { }
    }
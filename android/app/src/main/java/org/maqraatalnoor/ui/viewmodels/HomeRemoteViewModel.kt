package org.maqraatalnoor.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maqraatalnoor.data.remote.SessionHolder
import org.maqraatalnoor.data.remote.SupabaseApi
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.util.Locale

/** بطاقة الحلقة القادمة وأرقام الأسبوع، من قاعدة البيانات. */
class HomeRemoteViewModel : ViewModel() {

    data class GroupSummary(
        val id: String,
        val name: String,
        val foundation: Int = 0,
        val tajweed: Int = 0,
        val hifz: Int = 0,
    ) {
        val total: Int get() = foundation + tajweed + hifz
    }

    data class UiState(
        val loading: Boolean = true,
        val nextTitle: String = "لا حلقة مجدولة",
        val nextWhen: String = "",
        val sessionsThisWeek: Int = 0,
        val averageTajweed: String = "—",
        val myAttendance: Int = 0,
        val groups: List<GroupSummary> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        val remote = SessionHolder.remote ?: return
        val token = SessionHolder.session?.accessToken ?: return
        val me = SessionHolder.userId
        viewModelScope.launch {
            runCatching {
                val weekAgo = Instant.now().minusSeconds(7 * 24 * 3600).toString()

                val sessions = withContext(Dispatchers.IO) {
                    val rows = SupabaseApi.get(
                        "sessions?select=id,scheduled_at,status,current_page&order=scheduled_at.asc",
                        token,
                    )
                    buildList {
                        for (i in 0 until rows.length()) {
                            val row = rows.optJSONObject(i) ?: continue
                            add(
                                Triple(
                                    row.optString("status"),
                                    row.optString("scheduled_at"),
                                    row.optInt("current_page", 0),
                                ),
                            )
                        }
                    }
                }

                val evaluations = withContext(Dispatchers.IO) {
                    SupabaseApi.get(
                        "evaluations?select=tajweed,created_at&student_id=eq.$me&created_at=gte.$weekAgo",
                        token,
                    )
                }
                val attendance = withContext(Dispatchers.IO) {
                    SupabaseApi.get(
                        "session_attendance?select=status&user_id=eq.$me&status=eq.present",
                        token,
                    )
                }

                val groupRows = withContext(Dispatchers.IO) {
                    SupabaseApi.get("groups?select=id,name&order=name.asc", token)
                }
                val enrollmentRows = withContext(Dispatchers.IO) {
                    SupabaseApi.get("member_groups?select=group_id,level", token)
                }

                val groups = (0 until groupRows.length()).mapNotNull { groupRows.optJSONObject(it) }.map { group ->
                    val gid = group.optString("id")
                    var f = 0; var tj = 0; var h = 0
                    for (i in 0 until enrollmentRows.length()) {
                        val row = enrollmentRows.optJSONObject(i) ?: continue
                        if (row.optString("group_id") != gid) continue
                        when (row.optString("level")) {
                            "foundation" -> f++
                            "tajweed" -> tj++
                            "hifz" -> h++
                        }
                    }
                    GroupSummary(gid, group.optString("name"), f, tj, h)
                }

                val upcoming = sessions.firstOrNull { it.first == "scheduled" || it.first == "live" }
                val weekCount = sessions.count { it.second >= weekAgo }
                val scores = (0 until evaluations.length())
                    .mapNotNull { evaluations.optJSONObject(it)?.optInt("tajweed") }
                val average = if (scores.isEmpty()) "—" else "%.1f".format(Locale.US, scores.average())

                _state.value = UiState(
                    loading = false,
                    nextTitle = when (upcoming?.first) {
                        "live" -> "الحلقة جارية الآن"
                        "scheduled" -> "الحلقة القادمة"
                        else -> "لا حلقة مجدولة"
                    },
                    nextWhen = upcoming?.second?.let { formatWhen(it) } ?: "لم تُجدول جلسة بعد",
                    sessionsThisWeek = weekCount,
                    averageTajweed = average,
                    myAttendance = attendance.length(),
                    groups = groups,
                )
            }.onFailure {
                _state.value = _state.value.copy(loading = false)
            }
        }
    }

    private fun formatWhen(iso: String): String = runCatching {
        val instant = Instant.parse(iso)
        val zoned = instant.atZone(ZoneId.systemDefault())
        val day = when (zoned.dayOfWeek.value) {
            1 -> "الاثنين"
            2 -> "الثلاثاء"
            3 -> "الأربعاء"
            4 -> "الخميس"
            5 -> "الجمعة"
            6 -> "السبت"
            else -> "الأحد"
        }
        "$day • " + zoned.format(DateTimeFormatter.ofPattern("HH:mm"))
    }.getOrDefault(iso)
}

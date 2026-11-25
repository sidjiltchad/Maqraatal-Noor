package org.maqraatalnoor.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maqraatalnoor.data.remote.HalqaGroup
import org.maqraatalnoor.data.remote.RemoteMember
import org.maqraatalnoor.data.remote.SessionHolder
import org.maqraatalnoor.data.remote.setGraduated
import org.maqraatalnoor.data.remote.SupabaseApi
import org.json.JSONObject

/** مجموعات المستويات: التأسيس، التجويد، الحفظ، الإجازة — تنقل بينها المعلمة. */
class GroupsViewModel : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val students: List<RemoteMember> = emptyList(),
        val saving: String? = null,
        val error: String? = null,
        val notice: String? = null,
        val studyGroups: List<StudyGroup> = emptyList(),
    ) {
    }

    data class StudyGroup(val id: String, val name: String, val studentsByLevel: Map<String, List<String>>)

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    val canEdit: Boolean get() = SessionHolder.profile?.role == "supervisor"
    val canSuggest: Boolean get() = SessionHolder.profile?.role == "teacher"

    init {
        load()
    }

    fun load() {
        val remote = SessionHolder.remote ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                val members = withContext(Dispatchers.IO) { remote.members() }
                val token = SessionHolder.session?.accessToken.orEmpty()
                val groupRows = withContext(Dispatchers.IO) { SupabaseApi.get("groups?select=id,name&order=name.asc", token) }
                val enrollmentRows = withContext(Dispatchers.IO) { SupabaseApi.get("member_groups?select=user_id,group_id,level", token) }
                val nameById = members.associate { it.userId to it.name }
                val enrolled = buildMap<String, MutableMap<String, MutableList<String>>> {
                    for (i in 0 until enrollmentRows.length()) {
                        val row = enrollmentRows.optJSONObject(i) ?: continue
                        val groupId = row.optString("group_id")
                        val level = row.optString("level")
                        val name = nameById[row.optString("user_id")] ?: continue
                        getOrPut(groupId) { mutableMapOf() }.getOrPut(level) { mutableListOf() }.add(name)
                    }
                }
                val studyGroups = (0 until groupRows.length()).mapNotNull { groupRows.optJSONObject(it) }
                    .map { row ->
                        val id = row.optString("id")
                        val levels = if (SessionHolder.profile?.role == "student") {
                            buildMap {
                                for (i in 0 until enrollmentRows.length()) {
                                    val enrollment = enrollmentRows.optJSONObject(i) ?: continue
                                    if (enrollment.optString("user_id") == SessionHolder.userId && enrollment.optString("group_id") == id) {
                                        put(enrollment.optString("level"), listOf(SessionHolder.profile?.name ?: "أنتِ"))
                                    }
                                }
                            }
                        } else enrolled[id]?.mapValues { it.value.toList() }.orEmpty()
                        StudyGroup(id, row.optString("name"), levels)
                    }
                    .filter { SessionHolder.profile?.role != "student" || it.studentsByLevel.isNotEmpty() }
                _state.value = UiState(
                    loading = false,
                    students = members.filter { it.role == "student" },
                    studyGroups = studyGroups,
                )
            }.onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
        }
    }

    fun assign(userId: String, groupId: String, level: HalqaGroup) {
        if (!canEdit) return
        val token = SessionHolder.session?.accessToken ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = userId, error = null, notice = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    SupabaseApi.rpc(
                        "assign_member_group",
                        JSONObject().put("p_user", userId).put("p_group", groupId).put("p_level", level.key),
                        token,
                    )
                }
                _state.value = _state.value.copy(saving = null, notice = "حُدّث انضمام الطالبة إلى المجموعة والمستوى")
                load()
            }.onFailure { _state.value = _state.value.copy(saving = null, error = it.message) }
        }
    }

    fun propose(userId: String, groupId: String, level: HalqaGroup) {
        if (!canSuggest) return
        val token = SessionHolder.session?.accessToken ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = userId, error = null, notice = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    SupabaseApi.rpc(
                        "propose_group",
                        JSONObject().put("p_user", userId).put("p_group", groupId).put("p_level", level.key),
                        token,
                    )
                }
                _state.value = _state.value.copy(saving = null, notice = "أُرسل اقتراح النقل إلى المشرفة للمراجعة")
            }.onFailure { _state.value = _state.value.copy(saving = null, error = it.message) }
        }
    }

    fun graduate(student: RemoteMember, graduated: Boolean) {
        if (!canEdit) return
        val remote = SessionHolder.remote ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = student.userId, error = null, notice = null)
            runCatching {
                withContext(Dispatchers.IO) { remote.setGraduated(student.userId, graduated) }
                _state.value = _state.value.copy(
                    students = _state.value.students.map { member: RemoteMember ->
                        if (member.userId == student.userId) member.copy(graduated = graduated) else member
                    },
                    saving = null,
                    notice = if (graduated) "مُنحت  الإجازة (تخرّج)" else "أُلغيت إجازة ",
                )
            }.onFailure { _state.value = _state.value.copy(saving = null, error = it.message) }
        }
    }

}

package org.maqraatalnoor.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.maqraatalnoor.data.remote.HalqaGroup
import org.maqraatalnoor.data.remote.SessionHolder
import org.maqraatalnoor.data.remote.SupabaseApi

/** لوحة إدارة المشرفة العامة: الطلبات، المجموعات، الطالبات، الاقتراحات. */
class AdminViewModel : ViewModel() {

    data class Person(val id: String, val name: String, val status: String, val groups: String)
    data class Group(val id: String, val name: String)
    data class History(val id: String, val name: String, val accepted: Boolean, val code: String)

    data class Request(
        val id: String,
        val name: String,
        val email: String,
        val phone: String,
        val city: String,
        val level: String,
        val note: String,
    )

    data class Proposal(val id: String, val studentId: String, val groupId: String, val level: String, val student: String, val group: String, val note: String, val by: String)

    data class UiState(
        val loading: Boolean = true,
        val pending: List<Person> = emptyList(),
        val students: List<Person> = emptyList(),
        val groups: List<Group> = emptyList(),
        val proposals: List<Proposal> = emptyList(),
        val requests: List<Request> = emptyList(),
        val history: List<History> = emptyList(),
        val applicationsOpen: Boolean = false,
        val error: String? = null,
        val notice: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    /**
     * يقرأ التوكن مرة واحدة قبل إطلاق الطلبات؛ لا يُحوَّل غياب الجلسة
     * إلى توكن فارغ قد يصل إلى الواجهات المحمية.
     */
    private fun activeToken(): String? =
        SessionHolder.session?.accessToken?.takeIf { it.isNotBlank() }

    init { load() }

    fun load() {
        val token = activeToken() ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                val members = withContext(Dispatchers.IO) {
                    SupabaseApi.get(
                        "halqa_member_directory?select=user_id,role,status,full_name&order=status.desc",
                        token,
                    )
                }
                val groupsRows = withContext(Dispatchers.IO) {
                    SupabaseApi.get("groups?select=id,name&order=name.asc", token)
                }
                val enrollmentRows = withContext(Dispatchers.IO) {
                    SupabaseApi.get("member_groups?select=user_id,group_id,level", token)
                }
                val requestRows = withContext(Dispatchers.IO) {
                    SupabaseApi.get("join_requests?select=id,full_name,email,phone,city,level_pref,note,status&status=eq.pending&order=created_at.desc", token)
                }
                val historyRows = withContext(Dispatchers.IO) {
                    SupabaseApi.get(
                        "join_requests?select=id,full_name,status,invitation_code,decided_at" +
                            "&status=neq.pending&order=decided_at.desc&limit=20",
                        token,
                    )
                }
                val openRow = withContext(Dispatchers.IO) {
                    SupabaseApi.get("halqa?select=applications_open&limit=1", token)
                }
                val proposalRows = withContext(Dispatchers.IO) {
                    SupabaseApi.get(
                        "group_proposals?select=id,note,status,level,student_id,group_id,proposed_by" +
                            "&status=eq.pending",
                        token,
                    )
                }

                val groups = (0 until groupsRows.length()).mapNotNull { groupsRows.optJSONObject(it) }
                    .map {
                        Group(
                            id = it.optString("id"),
                            name = it.optString("name"),
                        )
                    }

                val groupNames = groups.associate { it.id to it.name }
                val labelsByUser = buildMap<String, MutableList<String>> {
                    for (i in 0 until enrollmentRows.length()) {
                        val row = enrollmentRows.optJSONObject(i) ?: continue
                        val label = groupNames[row.optString("group_id")] ?: continue
                        val level = when (row.optString("level")) {
                            "foundation" -> "التأسيس"
                            "tajweed" -> "التجويد"
                            "hifz" -> "الحفظ"
                            else -> continue
                        }
                        getOrPut(row.optString("user_id")) { mutableListOf() }.add("$label • $level")
                    }
                }
                fun person(row: JSONObject): Person {
                    val id = row.optString("user_id")
                    val name = row.optString("full_name").orEmpty()
                    return Person(id, name.ifBlank { "عضوة" }, row.optString("status"), labelsByUser[id].orEmpty().joinToString("، "))
                }
                val people = (0 until members.length())
                    .mapNotNull { members.optJSONObject(it) }
                    .filter { it.optString("role") == "student" }
                    .map { person(it) }

                val names = people.associate { it.id to it.name }
                val proposals = (0 until proposalRows.length())
                    .mapNotNull { proposalRows.optJSONObject(it) }
                    .map {
                        Proposal(
                            id = it.optString("id"),
                            studentId = it.optString("student_id"),
                            groupId = it.optString("group_id"),
                            level = it.optString("level", "foundation"),
                            student = names[it.optString("student_id")] ?: "طالبة",
                            group = groups.firstOrNull { g -> g.id == it.optString("group_id") }?.name
                                ?: "بلا مجموعة",
                            note = it.optString("note"),
                            by = "معلمة",
                        )
                    }

                _state.value = UiState(
                    loading = false,
                    pending = people.filter { it.status == "pending" },
                    students = people.filter { it.status != "pending" },
                    groups = groups,
                    proposals = proposals,
                    requests = (0 until requestRows.length()).mapNotNull { requestRows.optJSONObject(it) }.map {
                        Request(
                            id = it.optString("id"),
                            name = it.optString("full_name"),
                            email = it.optString("email"),
                            phone = it.optString("phone"),
                            city = it.optString("city"),
                            level = it.optString("level_pref"),
                            note = it.optString("note"),
                        )
                    },
                    history = (0 until historyRows.length()).mapNotNull { historyRows.optJSONObject(it) }.map {
                        History(
                            id = it.optString("id"),
                            name = it.optString("full_name"),
                            accepted = it.optString("status") == "accepted",
                            code = it.optString("invitation_code"),
                        )
                    },
                    applicationsOpen = openRow.optJSONObject(0)?.optBoolean("applications_open", false) ?: false,
                )
            }.onFailure {
                _state.value = _state.value.copy(loading = false, error = it.message)
            }
        }
    }

    private fun rpc(name: String, body: JSONObject, notice: String) {
        val token = activeToken() ?: return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { SupabaseApi.rpc(name, body, token) }
                _state.value = _state.value.copy(notice = notice)
                load()
            }.onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun toggleApplications(open: Boolean) = rpc(
        "set_applications_open",
        JSONObject().put("p_open", open),
        if (open) "الطلبات مفتوحة الآن" else "أُغلقت الطلبات",
    )

    fun decideRequest(request: Request, accept: Boolean) {
        val token = activeToken() ?: return
        viewModelScope.launch {
            runCatching {
                val code = withContext(Dispatchers.IO) {
                    SupabaseApi.rpc(
                        "decide_join_request",
                        JSONObject().put("p_id", request.id).put("p_accept", accept),
                        token,
                    )
                }
                _state.value = _state.value.copy(
                    notice = if (accept) "قُبل الطلب — كود الدعوة: $code" else "رُفض الطلب",
                )
                load()
            }.onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun decide(userId: String, accept: Boolean) = rpc(
        if (accept) "accept_member" else "reject_member",
        JSONObject().put("p_user", userId),
        if (accept) "قُبلت الطالبة" else "رُفض الطلب",
    )

    fun setStatus(userId: String, status: String, label: String) = rpc(
        "set_member_status",
        JSONObject().put("p_user", userId).put("p_status", status),
        label,
    )

    fun createGroup(name: String) {
        if (name.isBlank()) return
        rpc(
            "create_group",
            JSONObject().put("p_name", name.trim()),
            "أُنشئت المجموعة",
        )
    }

    fun assign(userId: String, groupId: String, level: HalqaGroup) = rpc(
        "assign_member_group",
        JSONObject().put("p_user", userId).put("p_group", groupId).put("p_level", level.key),
        "أُسندت الطالبة إلى المجموعة والمستوى",
    )

    fun decideProposal(id: String, accept: Boolean, studentId: String, groupId: String) {
        val token = activeToken() ?: return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    SupabaseApi.rpc(
                        "decide_proposal",
                        JSONObject().put("p_id", id).put("p_accept", accept),
                        token,
                    )
                }
                _state.value = _state.value.copy(notice = if (accept) "نُفِّذ الاقتراح" else "رُفض الاقتراح")
                load()
            }.onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }
}

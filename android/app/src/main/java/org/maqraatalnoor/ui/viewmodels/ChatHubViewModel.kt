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
import org.maqraatalnoor.data.remote.RemoteMember
import org.maqraatalnoor.data.remote.SessionHolder
import org.maqraatalnoor.data.remote.sendPrivateMessage

/** محادثة في القائمة: عامة للحلقة أو خاصة بعضوة. */
data class Conversation(
    val id: String,
    val title: String,
    val subtitle: String,
    val isGroup: Boolean,
    val memberId: String? = null,
    val initial: String = "ح",
)

/** مركز المحادثات على نمط Teams: قائمة محادثات + خيط رسائل حقيقي. */
class ChatHubViewModel : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val me: String = "",
        val myName: String = "",
        val conversations: List<Conversation> = emptyList(),
        val openConversation: Conversation? = null,
        val messages: List<RemoteMessageUi> = emptyList(),
        val sending: Boolean = false,
        val error: String? = null,
        val isStaff: Boolean = false,
        val isSupervisor: Boolean = false,
        val memberNames: Map<String, String> = emptyMap(),
    )

    data class RemoteMessageUi(
        val id: String,
        val body: String,
        val mine: Boolean,
        val senderName: String,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()
    private var poller: Job? = null

    fun start() {
        if (poller?.isActive == true) return
        poller = viewModelScope.launch {
            loadConversations()
            while (isActive) {
                _state.value.openConversation?.let { loadMessages(it, silent = true) }
                delay(3_000)
            }
        }
    }

    fun stop() {
        poller?.cancel()
        poller = null
    }

    private suspend fun loadConversations() {
        val remote = SessionHolder.remote ?: run {
            _state.value = UiState(loading = false, error = "لا توجد جلسة دخول")
            return
        }
        runCatching {
            val members = withContext(Dispatchers.IO) { remote.members() }
            val me = SessionHolder.userId
            val myName = SessionHolder.profile?.name.orEmpty()
            val isStaff = SessionHolder.isStaff
            val isSupervisor = SessionHolder.profile?.role == "supervisor"
            val others = members.filter {
                it.userId != me && if (isStaff) it.role == "student" else it.role != "student"
            }
            val conversations = buildList {
                add(
                    Conversation(
                        id = "halqa",
                        title = "محادثة المقرأة العامة",
                        subtitle = "محادثة جماعية لعضوات المقرأة",
                        isGroup = true,
                        initial = "ح",
                    ),
                )
                others.forEach { member ->
                    val ownerStudentId = if (isStaff) member.userId else me
                    add(
                        Conversation(
                            id = "p-${if (isStaff) member.userId else me}-${member.userId}",
                            title = member.name,
                            subtitle = if (isSupervisor) "محادثة خاصة • مقرئة ومشرفة" else roleLabel(member),
                            isGroup = false,
                            memberId = ownerStudentId,
                            initial = member.name.takeLast(1),
                        ),
                    )
                }
            }
            _state.value = _state.value.copy(
                loading = false,
                me = me,
                myName = myName,
                conversations = conversations,
                isStaff = isStaff,
                isSupervisor = isSupervisor,
                memberNames = members.associate { it.userId to it.name },
                error = null,
            )
        }.onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
    }

    fun open(conversation: Conversation) {
        _state.value = _state.value.copy(openConversation = conversation, messages = emptyList())
        viewModelScope.launch { loadMessages(conversation, silent = false) }
    }

    fun close() {
        _state.value = _state.value.copy(openConversation = null, messages = emptyList())
    }

    private suspend fun loadMessages(conversation: Conversation, silent: Boolean) {
        val remote = SessionHolder.remote ?: return
        runCatching {
            val rows = withContext(Dispatchers.IO) {
                if (conversation.isGroup) {
                    remote.messages("halqa")
                } else {
                    remote.messages("private", conversation.memberId)
                }
            }
            val mapped = rows.map {
                RemoteMessageUi(
                    id = it.id,
                    body = it.body,
                    mine = it.isMine,
                    senderName = if (it.isMine) "أنت" else _state.value.memberNames[it.senderId].orEmpty().ifBlank { conversation.title },
                )
            }
            _state.value = _state.value.copy(messages = mapped, error = null)
        }.onFailure {
            if (!silent) _state.value = _state.value.copy(error = it.message)
        }
    }

    fun send(body: String) {
        val remote = SessionHolder.remote ?: return
        val conversation = _state.value.openConversation ?: return
        val text = body.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(sending = true)
            runCatching {
                withContext(Dispatchers.IO) {
                    if (conversation.isGroup) {
                        remote.sendHalqaMessage(text)
                    } else {
                        remote.sendPrivateMessage(conversation.memberId.orEmpty(), text)
                    }
                }
                loadMessages(conversation, silent = false)
            }.onFailure { _state.value = _state.value.copy(error = it.message) }
            _state.value = _state.value.copy(sending = false)
        }
    }

    private fun roleLabel(member: RemoteMember): String = when (member.role) {
        "teacher" -> "معلمة"
        "supervisor" -> "المشرفة العامة"
        else -> when (member.level) {
            "foundation" -> "التأسيس"
            "tajweed" -> "التجويد"
            "hifz" -> "الحفظ"
            "ijaza" -> "الإجازة"
            else -> "طالبة"
        }
    }
}

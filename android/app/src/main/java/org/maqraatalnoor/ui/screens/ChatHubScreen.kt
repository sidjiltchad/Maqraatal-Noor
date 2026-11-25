package org.maqraatalnoor.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maqraatalnoor.data.MushafRepository
import org.maqraatalnoor.ui.components.MaqraatalNoorTopBar
import org.maqraatalnoor.ui.theme.CardLine
import org.maqraatalnoor.ui.theme.DeepGreen
import org.maqraatalnoor.ui.theme.Gold
import org.maqraatalnoor.ui.theme.GoldDark
import org.maqraatalnoor.ui.theme.GoldLight
import org.maqraatalnoor.ui.theme.Ink
import org.maqraatalnoor.ui.theme.LiveRed
import org.maqraatalnoor.ui.theme.MaqraatalNoorGreen
import org.maqraatalnoor.ui.theme.Muted
import org.maqraatalnoor.ui.theme.Paper
import org.maqraatalnoor.ui.viewmodels.ChatHubViewModel
import org.maqraatalnoor.ui.viewmodels.Conversation

/**
 * مركز المحادثات: قائمة محادثات ثم خيط رسائل،
 * مع لوحة مصحف **داخلية** (لا تفتح شاشة كاملة) للمشاركة أثناء الحوار.
 */
@Composable
fun ChatHubScreen(
    onBack: () -> Unit,
    onStartCall: (conversation: Conversation, video: Boolean) -> Unit,
    onOpenMushaf: () -> Unit = {},
    viewModel: ChatHubViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    DisposableEffect(Unit) {
        viewModel.start()
        onDispose { viewModel.stop() }
    }

    val open = state.openConversation
    if (open == null) {
        ConversationList(state, viewModel, onBack)
    } else {
        BackHandler { viewModel.close() }
        ChatThread(
            state = state,
            conversation = open,
            onBack = { viewModel.close() },
            onStartCall = onStartCall,
            onSend = viewModel::send,
        )
    }
}

/* ------------------------------------------------------------ القائمة ---- */

@Composable
private fun ConversationList(state: ChatHubViewModel.UiState, viewModel: ChatHubViewModel, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("الكل") }
    val filtered = state.conversations.filter {
        (query.isBlank() || it.title.contains(query, ignoreCase = true)) && when (filter) {
            "المجموعات" -> it.isGroup
            "الخاصة" -> !it.isGroup
            else -> true
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MaqraatalNoorTopBar("المحادثات", if (state.isSupervisor) "إشراف وإقراء • تواصل مع الطالبات والمعلمات" else "مساحة آمنة للتواصل والتعلّم", onBack)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("ابحثي عن محادثة أو عضوة…") },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("الكل", "المجموعات", "الخاصة").forEach { label ->
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(18.dp))
                        .background(if (filter == label) DeepGreen else Color(0x12000000))
                        .clickable { filter = label }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) { Text(label, color = if (filter == label) GoldLight else Muted, style = MaterialTheme.typography.labelMedium) }
            }
        }
        if (state.loading) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GoldDark)
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!state.loading && filtered.isEmpty()) {
                item {
                    Text("لا توجد محادثات تطابق البحث حالياً.", color = Muted, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().padding(20.dp))
                }
            }
            items(filtered) { conversation ->
                ConversationRow(conversation) { viewModel.open(conversation) }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun ConversationRow(conversation: Conversation, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Paper)
            .border(1.dp, CardLine, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(if (conversation.isGroup) RoundedCornerShape(14.dp) else CircleShape)
                .background(if (conversation.isGroup) DeepGreen else MaqraatalNoorGreen),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                conversation.initial,
                color = GoldLight,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                conversation.title,
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                conversation.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text("‹", style = MaterialTheme.typography.titleLarge, color = GoldDark)
    }
}

/* ------------------------------------------------------------ الخيط ----- */

@Composable
private fun ChatThread(
    state: ChatHubViewModel.UiState,
    conversation: Conversation,
    onBack: () -> Unit,
    onStartCall: (conversation: Conversation, video: Boolean) -> Unit,
    onSend: (String) -> Unit,
) {
    var draft by remember(conversation.id) { mutableStateOf("") }
    var showMushaf by remember(conversation.id) { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        ThreadHeader(conversation, onBack, onStartCall, showMushaf) { showMushaf = !showMushaf }

        if (showMushaf) {
            InlineMushafPanel(onClose = { showMushaf = false })
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { DayDivider() }
            if (state.messages.isEmpty() && !state.loading) {
                item {
                    Text(
                        "لا رسائل بعد — ابدئي بسلام أو مشاركة صفحة من المصحف.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                    )
                }
            }
            items(state.messages) { message -> MessageBubble(message) }
        }

        state.error?.let {
            Text(
                it,
                color = LiveRed,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        Composer(draft, onDraftChange = { draft = it }, onSend = {
            if (draft.isNotBlank()) {
                onSend(draft)
                draft = ""
            }
        })
    }
}

@Composable
private fun ThreadHeader(
    conversation: Conversation,
    onBack: () -> Unit,
    onStartCall: (Conversation, Boolean) -> Unit,
    mushafOpen: Boolean,
    onToggleMushaf: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DeepGreen)
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCircle("‹", filled = false, onClick = onBack)
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(if (conversation.isGroup) RoundedCornerShape(12.dp) else CircleShape)
                .background(MaqraatalNoorGreen),
            contentAlignment = Alignment.Center,
        ) {
            Text(conversation.initial, color = GoldLight, style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                conversation.title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                conversation.subtitle,
                color = GoldLight,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconCircle("📖", filled = mushafOpen, onClick = onToggleMushaf)
        Spacer(Modifier.width(6.dp))
        IconCircle("☎", filled = false, onClick = { onStartCall(conversation, false) })
        Spacer(Modifier.width(6.dp))
        IconCircle("▣", filled = false, onClick = { onStartCall(conversation, true) })
    }
}

@Composable
private fun IconCircle(symbol: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (filled) Gold else Color(0x22FFFFFF))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            symbol,
            color = if (filled) DeepGreen else GoldLight,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun DayDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x11000000))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text("اليوم", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

@Composable
private fun MessageBubble(message: ChatHubViewModel.RemoteMessageUi) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.mine) Arrangement.Start else Arrangement.End,
    ) {
        Column(horizontalAlignment = if (message.mine) Alignment.Start else Alignment.End) {
            if (!message.mine) {
                Text(
                    message.senderName,
                    style = MaterialTheme.typography.bodySmall,
                    color = GoldDark,
                    modifier = Modifier.padding(start = 6.dp, bottom = 2.dp),
                )
            }
            Box(
                modifier = Modifier
                    .widthIn(max = 290.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = if (message.mine) 6.dp else 18.dp,
                            bottomEnd = if (message.mine) 18.dp else 6.dp,
                        ),
                    )
                    .background(if (message.mine) MaqraatalNoorGreen else Paper)
                    .border(
                        1.dp,
                        if (message.mine) MaqraatalNoorGreen else CardLine,
                        RoundedCornerShape(18.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    message.body,
                    color = if (message.mine) Color.White else Ink,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun Composer(draft: String, onDraftChange: (String) -> Unit, onSend: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Paper)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            placeholder = { Text("اكتبي رسالة…") },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(24.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(Gold)
                .clickable { onSend() },
            contentAlignment = Alignment.Center,
        ) {
            Text("↑", color = DeepGreen, style = MaterialTheme.typography.titleLarge)
        }
    }
}

/* ------------------------------------------------------ مصحف مصغّر ---- */

/**
 * إطار مصحف داخل المحادثة: صفحة واحدة قابلة للتنقل، بارتفاع محدود،
 * بلا فتح الشاشة الكاملة، مع زر إغلاق.
 */
@Composable
private fun InlineMushafPanel(onClose: () -> Unit) {
    val context = LocalContext.current
    var page by remember { mutableStateOf(5) }
    val repository = remember { MushafRepository(context.assets) }
    val source = remember { repository.source("reading") }
    val path = remember(source, page) {
        source?.let { repository.pagePath(it, page.coerceIn(1, it.pageCount)) }
    }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            path?.let {
                runCatching {
                    context.assets.open(it).use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }.getOrNull()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Paper)
            .border(1.dp, Gold.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "المصحف • صفحة $page",
                style = MaterialTheme.typography.labelLarge,
                color = GoldDark,
            )
            Spacer(Modifier.weight(1f))
            SmallChip("−") { page = (page - 1).coerceAtLeast(1) }
            Spacer(Modifier.width(6.dp))
            SmallChip("+") { page = (page + 1).coerceAtMost(source?.pageCount ?: 604) }
            Spacer(Modifier.width(6.dp))
            SmallChip("✕") { onClose() }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = "صفحة $page من المصحف",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CircularProgressIndicator(color = GoldDark, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SmallChip(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Color(0x14000000))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = DeepGreen, style = MaterialTheme.typography.labelLarge)
    }
}

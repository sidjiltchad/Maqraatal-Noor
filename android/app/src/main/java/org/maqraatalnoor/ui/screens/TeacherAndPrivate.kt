package org.maqraatalnoor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.key
import org.maqraatalnoor.ui.viewmodels.TeacherRemoteViewModel
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import org.maqraatalnoor.ui.viewmodels.PrivateChatViewModel
import org.maqraatalnoor.ui.theme.Paper
import org.maqraatalnoor.ui.theme.Ink
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.maqraatalnoor.data.DemoRepository
import org.maqraatalnoor.data.TurnState
import org.maqraatalnoor.ui.components.GoldButton
import org.maqraatalnoor.ui.components.InitialAvatar
import org.maqraatalnoor.ui.components.MaqraatalNoorCard
import org.maqraatalnoor.ui.components.MaqraatalNoorTopBar
import org.maqraatalnoor.ui.components.SectionTitle
import org.maqraatalnoor.ui.theme.CardLine
import org.maqraatalnoor.ui.theme.DeepGreen
import org.maqraatalnoor.ui.theme.Gold
import org.maqraatalnoor.ui.theme.GoldDark
import org.maqraatalnoor.ui.theme.HighlightGold
import org.maqraatalnoor.ui.theme.LiveRed
import org.maqraatalnoor.ui.theme.MaqraatalNoorGreen
import org.maqraatalnoor.ui.theme.Muted
import org.maqraatalnoor.ui.theme.QuranFamily
import org.maqraatalnoor.ui.viewmodels.PrivateSessionViewModel
import org.maqraatalnoor.ui.viewmodels.TeacherViewModel

/** Screen 6 — teacher control, on real Supabase data. */
@Composable
fun TeacherControlScreen(
    classId: String,
    onBack: () -> Unit,
    viewModel: TeacherRemoteViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(classId) { viewModel.refresh(classId) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        MaqraatalNoorTopBar(
            "تحكم المعلمة",
            "الحلقة العامة • ${state.students.size} طالبة • ${if (state.session?.status == "live") "مباشرة" else "لم تبدأ"}",
        )
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TeacherPill(
                    text = "حاضرات ${state.presentCount}",
                    modifier = Modifier.weight(1f),
                    container = MaqraatalNoorGreen,
                )
                TeacherPill(
                    text = "غائبات ${state.absentCount}",
                    modifier = Modifier.weight(1f),
                    container = MaqraatalNoorGreen,
                )
                TeacherPill(
                    text = "رجوع",
                    modifier = Modifier.weight(1f),
                    container = LiveRed,
                    onClick = onBack,
                )
            }

            if (state.loading) {
                Spacer(Modifier.height(24.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GoldDark)
                }
            }
            state.error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = LiveRed, style = MaterialTheme.typography.bodyMedium)
            }
            state.message?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = GoldDark, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(16.dp))
            SectionTitle("الحضور والغياب")
            Spacer(Modifier.height(8.dp))

            state.students.forEach { student ->
                val present = state.attendance[student.userId] != false
                val turn = state.turns.firstOrNull { it.studentId == student.userId }
                val label = when {
                    turn?.state == "reading" -> "تقرأ الآن"
                    turn?.handRaised == true -> "رافعة يدها"
                    !present -> "غائبة"
                    else -> levelLabel(student.level)
                }
                key(student.userId) {
                    AttendanceRow(
                        initial = student.name.takeLast(1),
                        name = student.name,
                        state = label,
                        present = present,
                        onToggle = { viewModel.toggleAttendance(student.userId) },
                        onGiveTurn = { viewModel.giveTurn(student.userId) },
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle("التقييم السريع")
            Spacer(Modifier.height(8.dp))
            MaqraatalNoorCard {
                val reader = state.currentReader
                    ?.let { turn -> state.students.firstOrNull { it.userId == turn.studentId } }
                    ?: state.students.firstOrNull()
                Text(
                    "تقييم ${reader?.name ?: "—"}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EvalChip("4/5 تجويد", Modifier.weight(1f))
                    EvalChip("5/5 حفظ", Modifier.weight(1f))
                    EvalChip("حاضرة", Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(DeepGreen)
                        .clickable {
                            reader?.let { viewModel.saveEvaluation(it.userId, 4, 5, "تقييم الحلقة") }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "حفظ التقييم",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

private fun levelLabel(level: String): String = when (level) {
    "foundation" -> "التأسيس"
    "tajweed" -> "التجويد"
    "hifz" -> "الحفظ"
    "ijaza" -> "الإجازة"
    else -> level
}
/** Screen 7 — private thread between a teacher and one student (real data). */
@Composable
fun PrivateSessionScreen(
    studentId: String,
    onOpenMushaf: () -> Unit,
    viewModel: PrivateChatViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    var draft by remember { mutableStateOf("") }

    LaunchedEffect(studentId) { viewModel.startPolling(studentId) }
    DisposableEffect(studentId) { onDispose { viewModel.stopPolling() } }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        MaqraatalNoorTopBar(
            "جلسة فردية",
            state.studentName.ifBlank { "محادثة خاصة" },
        )
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(HighlightGold)
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "رسائل بين المعلمة والطالبة فقط • تُحفظ في قاعدة البيانات",
                    style = MaterialTheme.typography.bodySmall,
                    color = DeepGreen,
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (state.loading) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GoldDark)
            }
        }
        state.error?.let {
            Text(
                it,
                color = LiveRed,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.messages) { message ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (message.mine) Arrangement.Start else Arrangement.End,
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (message.mine) MaqraatalNoorGreen else Paper)
                            .border(
                                1.dp,
                                if (message.mine) MaqraatalNoorGreen else CardLine,
                                RoundedCornerShape(16.dp),
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

        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("اكتبي رسالتك…") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                singleLine = true,
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val text = draft.trim()
                    if (text.isNotEmpty()) {
                        viewModel.send(text)
                        draft = ""
                    }
                },
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = DeepGreen),
            ) {
                Text("إرسال", style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onOpenMushaf, modifier = Modifier.weight(1f)) {
                Text("المصحف", color = DeepGreen)
            }
            OutlinedButton(onClick = { viewModel.load(studentId) }, modifier = Modifier.weight(1f)) {
                Text("تحديث", color = DeepGreen)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
/** Deep pill used by the teacher's top controls (design screen 6). */
@Composable
private fun TeacherPill(
    text: String,
    modifier: Modifier = Modifier,
    container: Color,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(container)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/** One attendance card: avatar, name, live state, and the give-turn pill. */
@Composable
private fun AttendanceRow(
    initial: String,
    name: String,
    state: String,
    present: Boolean,
    onToggle: () -> Unit,
    onGiveTurn: () -> Unit,
) {
    MaqraatalNoorCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InitialAvatar(initial, size = 44, active = present)
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    state,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (present) Muted else GoldDark,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                if (present) "حاضرة" else "غائبة",
                color = if (present) DeepGreen else LiveRed,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Gold)
                    .clickable(onClick = onGiveTurn)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "أعطها الدور",
                    color = DeepGreen,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Outlined read-only chip in the quick-evaluation card. */
@Composable
private fun EvalChip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, CardLine, RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = DeepGreen,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

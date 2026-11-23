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

/** Screen 6 — teacher control: attendance, turns, quick evaluation. */
@Composable
fun TeacherControlScreen(
    classId: String,
    onBack: () -> Unit,
    viewModel: TeacherViewModel = viewModel(),
) {
    val records by viewModel.records.collectAsState()
    val evaluations by viewModel.evaluations.collectAsState()
    val turns by viewModel.turns.collectAsState()
    val savedMessage by viewModel.savedMessage.collectAsState()
    var locked by remember { mutableStateOf(false) }

    val students = DemoRepository.studentsInClass(classId)
    val summary = viewModel.summary()
    val currentReader = turns.firstOrNull { it.state == TurnState.READING }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        MaqraatalNoorTopBar("تحكم المعلمة", "حلقة التجويد • مباشر • 6 طالبات")
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TeacherPill(
                    text = "كتم الجميع",
                    modifier = Modifier.weight(1f),
                    container = MaqraatalNoorGreen,
                ) { viewModel.clearSavedMessage() }
                TeacherPill(
                    text = if (locked) "فتح الدخول" else "قفل الدخول",
                    modifier = Modifier.weight(1f),
                    container = MaqraatalNoorGreen,
                ) { locked = !locked }
                TeacherPill(
                    text = "إنهاء الحلقة",
                    modifier = Modifier.weight(1f),
                    container = LiveRed,
                    onClick = onBack,
                )
            }
            if (locked) {
                Spacer(Modifier.height(8.dp))
                Text("الدخول مقفل: لا انضمام جديد لهذه الحلقة.", style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
            Spacer(Modifier.height(16.dp))
            SectionTitle("الحضور والغياب")
            Spacer(Modifier.height(4.dp))
            Text(
                "حاضرات ${summary.present} من ${summary.total} • غائبات ${summary.absent}",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            students.forEach { student ->
                val present = records.firstOrNull { it.studentId == student.id }?.present ?: true
                val turn = turns.firstOrNull { it.student.id == student.id }
                val state = when {
                    currentReader?.student?.id == student.id -> "تقرأ الآن"
                    turn?.handRaised == true -> "رافعة يدها"
                    !present -> "لم تنضم بعد"
                    else -> "تستمع"
                }
                AttendanceRow(
                    initial = student.initial,
                    name = student.name,
                    state = state,
                    present = present,
                    onToggle = { viewModel.toggleAttendance(student.id) },
                    onGiveTurn = { viewModel.giveTurn(student.id) },
                )
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(12.dp))
            SectionTitle("التقييم السريع")
            Spacer(Modifier.height(8.dp))
            MaqraatalNoorCard {
                val readerName = currentReader?.student?.name ?: "—"
                val evaluation = currentReader?.let { evaluations[it.student.id] }
                Text(
                    "تقييم سريع لـ $readerName",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EvalChip("الحضور ✓", Modifier.weight(1f))
                    EvalChip("الحفظ ${evaluation?.hifzScore ?: 3}/5", Modifier.weight(1f))
                    EvalChip("التجويد ${evaluation?.tajweedScore ?: 4}/5", Modifier.weight(1f))
                }
                if (!evaluation?.note.isNullOrEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(evaluation.note, style = MaterialTheme.typography.bodyMedium, color = Muted)
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(25.dp))
                        .background(DeepGreen)
                        .clickable {
                            currentReader?.let { reader ->
                                viewModel.saveEvaluation(
                                    reader.student.id,
                                    evaluation ?: org.maqraatalnoor.data.Evaluation(reader.student.id, 4, 3, true),
                                )
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "حفظ التقييم وإرساله للطالبة",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (savedMessage != null) {
                Text(
                    savedMessage!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = GoldDark,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(
                "التقييم والحضور يُحفظان على هذا الجهاز فقط في هذه النسخة، ولا يُرسلان إلى خادم.",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Screen 7 — private 1-to-1 session: messages, voice recitation, evaluation. */
@Composable
fun PrivateSessionScreen(
    studentId: String,
    onOpenMushaf: () -> Unit,
    viewModel: PrivateSessionViewModel = viewModel(),
) {
    val messages by viewModel.messages.collectAsState()
    var draft by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }

    val student = DemoRepository.students.firstOrNull { it.id == studentId } ?: DemoRepository.students.first()

    Column(modifier = Modifier.fillMaxSize()) {
        MaqraatalNoorTopBar("جلسة فردية • ${student.name}", "إقراء • الخميس 18:30 • 25 دقيقة")
        // Session position card.
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(HighlightGold)
                    .padding(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("موضع اليوم • سورة الكهف، آية 1", style = MaterialTheme.typography.labelLarge, color = DeepGreen)
                    Text(
                        "ٱلْحَمْدُ لِلَّهِ ٱلَّذِىٓ أَنزَلَ عَلَىٰ عَبْدِهِ ٱلْكِتَٰبَ وَلَمْ يَجْعَل لَّهُۥ عِوَجًا",
                        fontFamily = QuranFamily,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages) { message ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    if (message.isTeacher) Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (message.isTeacher) DeepGreen else Color.White)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Column {
                            if (message.isTeacher) {
                                Text("المعلمة", color = Gold, style = MaterialTheme.typography.labelLarge)
                            }
                            if (message.isVoiceNote) {
                                Text(
                                    "▶ تسميع صوتي • ${"%02d:%02d".format(message.durationSeconds / 60, message.durationSeconds % 60)}",
                                    color = if (message.isTeacher) Color.White else DeepGreen,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.clickable {
                                        viewModel.playVoiceNote(message)
                                        notice = "التشغيل الصوتي غير متوفر في هذه النسخة؛ التسميع يُسجَّل ويُحفظ محليا في النسخة الكاملة."
                                    },
                                )
                            } else {
                                Text(
                                    message.text,
                                    color = if (message.isTeacher) Color.White else DeepGreen,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                    if (!message.isTeacher) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (notice != null) {
            Text(
                notice!!,
                style = MaterialTheme.typography.bodyMedium,
                color = GoldDark,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(6.dp))
        }
        Text(
            "الرسائل بين المعلمة والطالبة فقط • لا يراها أحد • محفوظة على الجهاز فقط",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
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
                onClick = { viewModel.send(draft); draft = "" },
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = DeepGreen),
            ) {
                Text("إرسال", style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { notice = "المكالمات الصوتية الحقيقية غير متوفرة في هذه النسخة؛ تحتاج خادم SFU وإشارات وحسابات." }, modifier = Modifier.weight(1f)) {
                Text("اتصال صوتي", color = DeepGreen)
            }
            OutlinedButton(onClick = { notice = "مكالمات الفيديو غير متوفرة في هذه النسخة؛ الكاميرا مغلقة افتراضيا حمايةً للخصوصية." }, modifier = Modifier.weight(1f)) {
                Text("اتصال صورة", color = DeepGreen)
            }
            OutlinedButton(onClick = onOpenMushaf, modifier = Modifier.weight(1f)) {
                Text("المصحف", color = DeepGreen)
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
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(container)
            .clickable(onClick = onClick)
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

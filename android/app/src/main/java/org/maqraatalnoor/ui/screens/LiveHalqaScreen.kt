package org.maqraatalnoor.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import org.maqraatalnoor.ui.theme.Cream
import org.maqraatalnoor.ui.theme.DeepGreen
import org.maqraatalnoor.ui.theme.DeepGreenDark
import org.maqraatalnoor.ui.theme.Gold
import org.maqraatalnoor.ui.theme.GoldDark
import org.maqraatalnoor.ui.theme.GoldLight
import org.maqraatalnoor.ui.theme.HighlightGold
import org.maqraatalnoor.ui.theme.LiveRed
import org.maqraatalnoor.ui.theme.MaqraatalNoorGreen
import org.maqraatalnoor.ui.theme.Muted
import org.maqraatalnoor.ui.theme.Paper
import org.maqraatalnoor.ui.theme.QuranFamily
import org.maqraatalnoor.ui.viewmodels.LiveHalqaViewModel

/**
 * Screen 4 — live halqa. The call state is a LOCAL SIMULATION only
 * (see call/CallSession): no audio or video is captured or sent in this
 * build. Camera starts off, as in the design.
 */
@Composable
fun LiveHalqaScreen(
    classId: String,
    onBack: () -> Unit,
    onOpenMushaf: () -> Unit,
    viewModel: LiveHalqaViewModel = viewModel(),
) {
    val callState by viewModel.callState.collectAsState()
    val turns by viewModel.turns.collectAsState()
    val isTeacherView by viewModel.isTeacherView.collectAsState()
    val timerSeconds by viewModel.timerSeconds.collectAsState()
    var chatNoteVisible by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        viewModel.join()
        onDispose { viewModel.leave() }
    }

    val classGroup = DemoRepository.classById(classId) ?: DemoRepository.classes.first()
    val currentReader = turns.firstOrNull { it.state == TurnState.READING }
    val minutes = timerSeconds / 60
    val seconds = timerSeconds % 60

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepGreenDark)
            .verticalScroll(rememberScrollState()),
    ) {
        // Header: title + timer + live badge.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(LiveRed)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text("مباشر", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "%02d:%02d".format(minutes, seconds),
                color = GoldLight,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "حلقة التجويد • مباشر",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            // Teacher tile.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaqraatalNoorGreen)
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    InitialAvatar("س", size = 72, active = true)
                    Spacer(Modifier.height(8.dp))
                    Text("المعلمة أم سارة", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "تقرأ: ${currentReader?.student?.name ?: "—"} • ${classGroup.surah}، آية 1",
                        color = Cream,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text("الكاميرا مغلقة • صوت فقط", color = GoldLight, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(12.dp))

            // Student tiles (initials only — no photos).
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                callState.participants.filter { !it.isTeacher }.forEach { participant ->
                    val turn = turns.firstOrNull { it.student.id == participant.id }
                    val status = when {
                        turn?.state == TurnState.READING -> "تقرأ الآن"
                        participant.handRaised -> "رافعة يدها"
                        else -> "تستمع"
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF103A2C))
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        InitialAvatar(participant.initial, size = 44, active = true)
                        Spacer(Modifier.height(6.dp))
                        Text(participant.name, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        Text(status, color = if (status == "تستمع") Cream else GoldLight, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // Synchronised Mushaf strip.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Paper)
                    .padding(16.dp),
            ) {
                Column {
                    Text(
                        "المصحف • ${classGroup.surah} • صفحة ${DemoRepository.nextHalqa.page}",
                        color = GoldDark,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(HighlightGold)
                            .padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            DemoRepository.nextHalqa.currentAyahText,
                            fontFamily = QuranFamily,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "متابعة المعلمة مفعّلة • الصفحة تنقلب عند الجميع معا",
                        color = Muted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onOpenMushaf, modifier = Modifier.fillMaxWidth()) {
                        Text("فتح المصحف", color = DeepGreen)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // Recitation queue.
            MaqraatalNoorCard {
                Text("دور التلاوة", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                turns.forEachIndexed { index, turn ->
                    val label = when (turn.state) {
                        TurnState.READING -> "تقرأ الآن"
                        TurnState.DONE -> "انتهت"
                        TurnState.WAITING -> if (turn.handRaised) "رافعة يدها" else "في الانتظار"
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${index + 1} • ${turn.student.name} — $label",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.End,
                        )
                        if (isTeacherView && turn.state == TurnState.WAITING) {
                            Spacer(Modifier.height(4.dp))
                            Button(
                                onClick = { viewModel.giveTurn(turn.student.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = DeepGreenDark),
                            ) {
                                Text("أعطها الدور")
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(4.dp))
                GoldButton(
                    text = if (callState.handRaised) "يدك مرفوعة — إنزالها" else "ارفعي يدك",
                    onClick = { if (callState.handRaised) viewModel.lowerHandForSelf() else viewModel.raiseHand() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(12.dp))

            // Teacher view switch (demo aid: the real app gates this by role).
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("عرض المعلمة", color = GoldLight, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = isTeacherView, onCheckedChange = { viewModel.setTeacherView(it) })
            }
            Spacer(Modifier.height(8.dp))
            if (isTeacherView) {
                Text("تحكم المعلمة", color = GoldLight, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TeacherControlButton("كتم الجميع", Modifier.weight(1f)) { viewModel.muteAll() }
                    TeacherControlButton("قفل الحلقة", Modifier.weight(1f)) { viewModel.setLocked(!callState.locked) }
                    TeacherControlButton("إنهاء", Modifier.weight(1f)) { onBack() }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (callState.locked) "الحلقة مقفلة: لا انضمام جديد." else "الحلقة مفتوحة للانضمام.",
                    color = Cream,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
            }

            // Call controls.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CallControlButton(
                    if (callState.selfMuted) "الميكروفون: مكتوم" else "الميكروفون: مفتوح",
                    Modifier.weight(1f),
                ) { viewModel.toggleMute() }
                CallControlButton(
                    if (callState.selfCameraOn) "الكاميرا: مفتوحة" else "الكاميرا: مغلقة",
                    Modifier.weight(1f),
                ) { viewModel.toggleCamera() }
                CallControlButton("الدردشة", Modifier.weight(1f)) { chatNoteVisible = !chatNoteVisible }
                Button(
                    onClick = onBack,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LiveRed, contentColor = Color.White),
                ) {
                    Text("مغادرة")
                }
            }
            if (chatNoteVisible) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "دردشة الحلقة الجماعية غير متوفرة في هذه النسخة؛ الرسائل الخاصة في الجلسة الفردية.",
                    color = Cream,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "مغلق: ميكروفونك مكتوم حتى تعطيك المعلمة الدور. نسخة محلية: حالة المكالمة محاكاة فقط، بلا صوت أو صورة حقيقيين.",
                color = Cream,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TeacherControlButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Gold),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldLight),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun CallControlButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaqraatalNoorGreen, contentColor = Color.White),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

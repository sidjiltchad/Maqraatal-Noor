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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.maqraatalnoor.data.remote.RemoteMember
import org.maqraatalnoor.data.remote.RemoteTurn
import org.maqraatalnoor.ui.components.InitialAvatar
import org.maqraatalnoor.ui.components.MaqraatalNoorCard
import org.maqraatalnoor.ui.theme.DeepGreen
import org.maqraatalnoor.ui.theme.DeepGreenDark
import org.maqraatalnoor.ui.theme.Gold
import org.maqraatalnoor.ui.theme.GoldDark
import org.maqraatalnoor.ui.theme.GoldLight
import org.maqraatalnoor.ui.theme.LiveRed
import org.maqraatalnoor.ui.theme.MaqraatalNoorGreen
import org.maqraatalnoor.ui.theme.Muted
import org.maqraatalnoor.ui.theme.Paper
import org.maqraatalnoor.ui.viewmodels.LiveSessionViewModel

/**
 * Screen 4 — الحلقة الحية، على الجلسة والطابور الحقيقيين من Supabase.
 * إدارة الدور والحضور والمصحف هنا، والصوت/الفيديو عبر مسار مكالمة LiveKit.
 */
@Composable
fun LiveHalqaScreen(
    classId: String,
    onBack: () -> Unit,
    onOpenMushaf: () -> Unit,
    onJoinCall: () -> Unit,
    viewModel: LiveSessionViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    DisposableEffect(classId) {
        viewModel.start(classId)
        onDispose { viewModel.stop() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepGreenDark)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Color.White)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (state.isLive) LiveRed else MaqraatalNoorGreen)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    if (state.isLive) "مباشر" else "لم تبدأ",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "الحلقة العامة",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            // المعلمات
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaqraatalNoorGreen)
                    .padding(16.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.teachers.forEach { teacher ->
                            InitialAvatar(
                                teacher.name.takeLast(1),
                                size = 52,
                                active = true,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.teachers.joinToString("، ") { it.name }.ifBlank { "لا معلمات" },
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    val reader = state.currentReader?.name
                    Text(
                        if (reader != null) "تقرأ الآن: $reader" else "لم تُسند القراءة بعد",
                        color = GoldLight,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "الكاميرا مغلقة • صوت فقط • ${state.presentCount} حاضرة",
                        color = GoldLight,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // الطالبات
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.students.take(4).forEach { student ->
                    StudentTile(
                        student = student,
                        turn = state.turns.firstOrNull { it.studentId == student.userId },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // شريط المصحف
            MaqraatalNoorCard {
                Text(
                    "المصحف • صفحة ${state.session?.page ?: 5}",
                    style = MaterialTheme.typography.titleMedium,
                    color = GoldDark,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "التنقل في المصحف مستقل على جهازك، والموضع المشترك يُدار من المعلمة.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onOpenMushaf, modifier = Modifier.fillMaxWidth()) {
                    Text("فتح المصحف", color = DeepGreen)
                }
            }

            Spacer(Modifier.height(12.dp))

            // الطابور
            MaqraatalNoorCard {
                Text(
                    "دور التلاوة",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth(),
                    fontWeight = FontWeight.Bold,
                    color = DeepGreen,
                )
                Spacer(Modifier.height(8.dp))
                if (state.queue.isEmpty()) {
                    Text(
                        "لا أحد في الطابور بعد.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                    )
                } else {
                    state.queue.forEach { (member, turn) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${turn.position}",
                                style = MaterialTheme.typography.labelLarge,
                                color = GoldDark,
                                modifier = Modifier.width(24.dp),
                            )
                            Text(
                                "${member.name} — ${turnLabel(turn)}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color(0xFF22261F),
                                modifier = Modifier.weight(1f),
                            )
                            if (viewModel.isStaff && turn.state != "reading") {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Gold)
                                        .clickable { viewModel.giveTurn(member.userId) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Text(
                                        "أعطها الدور",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = DeepGreen,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (state.loading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GoldLight)
                }
            }
            state.notice?.let {
                Text(it, color = GoldLight, style = MaterialTheme.typography.bodyMedium)
            }
            state.error?.let {
                Text(it, color = LiveRed, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .background(Gold)
                    .clickable { onJoinCall() },
                contentAlignment = Alignment.Center,
            ) {
                Text("دخول المكالمة الصوتية", color = DeepGreen, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!viewModel.isStaff) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Gold)
                            .clickable { viewModel.raiseHand() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("ارفعي يدك", color = DeepGreen, style = MaterialTheme.typography.labelLarge)
                    }
                }
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text("خروج", color = GoldLight)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

private fun turnLabel(turn: RemoteTurn): String = when {
    turn.state == "reading" -> "تقرأ الآن"
    turn.handRaised -> "رافعة يدها"
    turn.state == "done" -> "انتهى دورها"
    else -> "في الانتظار"
}

@Composable
private fun StudentTile(
    student: RemoteMember,
    turn: RemoteTurn?,
    modifier: Modifier = Modifier,
) {
    val reading = turn?.state == "reading"
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (reading) Gold.copy(alpha = 0.18f) else Color(0x1FFFFFFF))
            .border(
                1.dp,
                if (reading) Gold else Color(0x22FFFFFF),
                RoundedCornerShape(14.dp),
            )
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (reading) Gold else Color(0x33FFFFFF))
                .border(1.dp, Gold, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                student.name.takeLast(1),
                color = if (reading) DeepGreen else GoldLight,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            student.name,
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        Text(
            when {
                reading -> "تقرأ الآن"
                turn?.handRaised == true -> "رافعة يدها"
                else -> "تستمع"
            },
            color = if (reading) GoldLight else Color(0xFFB8C4B8),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
        )
    }
}

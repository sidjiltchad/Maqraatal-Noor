package org.maqraatalnoor.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import io.livekit.android.compose.ui.ScaleType
import io.livekit.android.compose.ui.VideoTrackView
import org.maqraatalnoor.ui.theme.DeepGreen
import org.maqraatalnoor.ui.theme.DeepGreenDark
import org.maqraatalnoor.ui.theme.Gold
import org.maqraatalnoor.ui.theme.GoldLight
import org.maqraatalnoor.ui.theme.LiveRed
import org.maqraatalnoor.ui.theme.MaqraatalNoorGreen
import org.maqraatalnoor.ui.viewmodels.CallViewModel

/**
 * غرفة المكالمة الصوتية (LiveKit): اتصال حقيقي بميكروفون،
 * مع واجهة حديثة: حالة الاتصال، المشاركات، وأزرار التحكم.
 */
@Composable
fun CallScreen(
    onLeave: () -> Unit,
    startWithVideo: Boolean = false,
    thread: String = "halqa",
    studentId: String = "",
    viewModel: CallViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val permissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted[Manifest.permission.RECORD_AUDIO] == true) {
            viewModel.join(
                startCamera = startWithVideo && granted[Manifest.permission.CAMERA] == true,
                thread = thread,
                studentId = studentId,
            )
        }
    }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.toggleCamera() }

    LaunchedEffect(Unit) {
        permissions.launch(
            if (startWithVideo) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
            else arrayOf(Manifest.permission.RECORD_AUDIO),
        )
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.leave() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepGreenDark)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StatusChip(state.phase)
            Spacer(Modifier.weight(1f))
            Text("الغرفة: ${state.room}", color = GoldLight, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(28.dp))
        Box(
            modifier = Modifier
                .size(148.dp)
                .clip(CircleShape)
                .background(MaqraatalNoorGreen)
                .border(2.dp, Gold, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            when (state.phase) {
                CallViewModel.Phase.CONNECTING -> CircularProgressIndicator(color = Gold)
                CallViewModel.Phase.CONNECTED -> Text(
                    if (state.micEnabled) "🎙" else "🔇",
                    style = MaterialTheme.typography.headlineLarge,
                )

                else -> Text("☎", style = MaterialTheme.typography.headlineLarge)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            when (state.phase) {
                CallViewModel.Phase.CONNECTING -> "جارٍ الاتصال بالغرفة…"
                CallViewModel.Phase.CONNECTED -> "أنتِ في الاتصال"
                CallViewModel.Phase.FAILED -> "تعذّر الاتصال"
                CallViewModel.Phase.CLOSED -> "انتهت المكالمة"
                else -> "المكالمة"
            },
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "مقرأة النور • صوت فقط • الكاميرا مغلقة افتراضيا",
            color = GoldLight,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )

        AnimatedVisibility(visible = state.error != null) {
            Box(
                modifier = Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x33C4302B))
                    .padding(12.dp),
            ) {
                Text(
                    state.error.orEmpty(),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        if (state.phase == CallViewModel.Phase.CONNECTED) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "المشاركات (${state.participants.size})",
                    color = GoldLight,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                state.participants.forEach { name ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0x1FFFFFFF))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaqraatalNoorGreen)
                                .border(1.dp, Gold, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                name.take(1),
                                color = GoldLight,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(name, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            if (state.videoFeeds.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    state.videoFeeds.forEach { feed ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            VideoTrackView(
                                videoTrack = feed.track,
                                modifier = Modifier.width(180.dp).height(220.dp).clip(RoundedCornerShape(18.dp)),
                                passedRoom = state.liveRoom,
                                mirror = feed.isLocal,
                                scaleType = ScaleType.Fill,
                            )
                            Text(feed.name, color = GoldLight, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ControlButton(
                    label = if (state.micEnabled) "كتم" else "فتح الصوت",
                    active = state.micEnabled,
                    modifier = Modifier.weight(1f),
                ) { viewModel.toggleMic() }
                ControlButton(
                    label = if (state.cameraEnabled) "إغلاق الكاميرا" else "الكاميرا",
                    active = state.cameraEnabled,
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.cameraEnabled || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        viewModel.toggleCamera()
                    } else {
                        cameraPermission.launch(Manifest.permission.CAMERA)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(LiveRed)
                    .clickable {
                        viewModel.leave()
                        onLeave()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("إنهاء المكالمة", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Gold)
                    .clickable {
                        permissions.launch(
                            if (startWithVideo) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
                            else arrayOf(Manifest.permission.RECORD_AUDIO),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (state.phase == CallViewModel.Phase.CONNECTING) "جارٍ الاتصال…" else "إعادة المحاولة",
                    color = DeepGreen,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, Gold.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                    .clickable { onLeave() },
                contentAlignment = Alignment.Center,
            ) {
                Text("رجوع", color = GoldLight, style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StatusChip(phase: CallViewModel.Phase) {
    val (label, color) = when (phase) {
        CallViewModel.Phase.CONNECTED -> "متصل" to LiveRed
        CallViewModel.Phase.CONNECTING -> "جارٍ الاتصال" to Gold.copy(alpha = 0.9f)
        CallViewModel.Phase.FAILED -> "فشل" to Color(0xFF9E2A25)
        else -> "غير متصل" to MaqraatalNoorGreen
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ControlButton(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(if (active) Gold else Color(0x22FFFFFF))
            .border(
                1.dp,
                if (active) Gold else Color(0x44FFFFFF),
                RoundedCornerShape(25.dp),
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active) DeepGreen else Color.White,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

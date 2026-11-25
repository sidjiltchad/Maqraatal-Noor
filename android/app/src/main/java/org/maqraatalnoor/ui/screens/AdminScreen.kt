package org.maqraatalnoor.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.maqraatalnoor.data.remote.HalqaGroup
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
import org.maqraatalnoor.ui.viewmodels.AdminViewModel

/**
 * لوحة إدارة المقرأة — واجهة المشرفة العامة:
 * مؤشرات سريعة، استقبال الطلبات، طلبات الالتحاق بالبيانات الكاملة،
 * المجموعات، الطالبات، واقتراحات المعلمات.
 */
@Composable
fun AdminScreen(
    onBack: () -> Unit,
    viewModel: AdminViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    var newGroupName by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        MaqraatalNoorTopBar("إدارة المقرأة", "القبول والمجموعات ومتابعة الطالبات", onBack)

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            HeaderStats(
                requests = state.requests.size,
                students = state.students.size,
                groups = state.groups.size,
                pending = state.pending.size,
            )

            state.error?.let {
                InfoBanner(it, Color(0x33C4302B))
            }
            state.notice?.let {
                InfoBanner(it, Color(0x2255A55F))
            }

            ApplicationsSwitch(state.applicationsOpen) { viewModel.toggleApplications(!state.applicationsOpen) }

            // ── طلبات الالتحاق ─────────────────────────────────────────────
            SectionTitle("طلبات الالتحاق", state.requests.size)
            if (state.requests.isEmpty()) {
                EmptyCard("لا طلبات جديدة حاليا")
            } else {
                state.requests.forEach { request ->
                    RequestCard(
                        name = request.name,
                        email = request.email,
                        phone = request.phone,
                        city = request.city,
                        level = request.level,
                        note = request.note,
                        onAccept = { viewModel.decideRequest(request, true) },
                        onReject = { viewModel.decideRequest(request, false) },
                    )
                }
            }

            // ── سجل القبول والرفض ──────────────────────────────────────────
            SectionTitle("سجل الطلبات", state.history.size)
            if (state.history.isEmpty()) {
                EmptyCard("لم يُتخذ قرار بعد")
            } else {
                state.history.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Paper)
                            .border(1.dp, CardLine, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (item.accepted) Color(0x2255A55F) else Color(0x22C4302B))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                if (item.accepted) "مقبول" else "مرفوض",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (item.accepted) Color(0xFF2E7D32) else LiveRed,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.bodyLarge, color = Ink)
                            if (item.accepted && item.code.isNotBlank() && item.code != "null") {
                                Text(
                                    "كود الدعوة: ${item.code}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GoldDark,
                                )
                            }
                        }
                    }
                }
            }

            // ── المجموعات ──────────────────────────────────────────────────
            SectionTitle("المجموعات", state.groups.size)
            if (state.groups.isEmpty()) {
                EmptyCard("لا مجموعات بعد — أنشئي أول مجموعة لتنظيم الطالبات")
            } else {
                state.groups.forEach { group ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Paper)
                            .border(1.dp, CardLine, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(MaqraatalNoorGreen),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("م", color = GoldLight, style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            group.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = newGroupName,
                onValueChange = { newGroupName = it },
                placeholder = { Text("اسم مجموعة جديد (مثل: مجموعة النور)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            PrimaryButton("إنشاء المجموعة") {
                viewModel.createGroup(newGroupName)
                newGroupName = ""
            }

            // ── الطالبات ───────────────────────────────────────────────────
            SectionTitle("الطالبات", state.students.size)
            if (state.students.isEmpty()) {
                EmptyCard("لا طالبات مقبولات بعد")
            } else {
                state.students.forEach { person ->
                    StudentCard(
                        name = person.name,
                        subtitle = person.groups.ifBlank { "بلا مستوى" },
                        banned = person.status == "banned",
                        groups = state.groups.map { it.id to it.name },
                        onBan = {
                            viewModel.setStatus(
                                person.id,
                                if (person.status == "banned") "active" else "banned",
                                if (person.status == "banned") "رُفع الحظر" else "حُظرت الطالبة",
                            )
                        },
                        onAssign = { groupId -> viewModel.assign(person.id, groupId, HalqaGroup.TAJWEED) },
                    )
                }
            }

            // ── الاقتراحات ─────────────────────────────────────────────────
            SectionTitle("اقتراحات المعلمات", state.proposals.size)
            if (state.proposals.isEmpty()) {
                EmptyCard("لا اقتراحات حاليا")
            } else {
                state.proposals.forEach { proposal ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Paper)
                            .border(1.dp, CardLine, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(proposal.student, style = MaterialTheme.typography.bodyLarge, color = Ink)
                            Text(
                                "إلى ${proposal.group}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Muted,
                            )
                        }
                        MiniButton("تنفيذ", true) { viewModel.decideProposal(proposal.id, true, "", "") }
                        Spacer(Modifier.width(6.dp))
                        MiniButton("رفض", false) { viewModel.decideProposal(proposal.id, false, "", "") }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            GhostButton("رجوع", onBack)
            Spacer(Modifier.height(18.dp))
        }
    }
}

/* ───────────────────────────── عناصر الواجهة ───────────────────────────── */

@Composable
private fun HeaderStats(requests: Int, students: Int, groups: Int, pending: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(DeepGreen)
            .padding(16.dp),
    ) {
        Text("نظرة سريعة", color = GoldLight, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCell("طلبات", requests.toString(), Modifier.weight(1f))
            StatCell("طالبات", students.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCell("مجموعات", groups.toString(), Modifier.weight(1f))
            StatCell("بانتظار التفعيل", pending.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x1AFFFFFF))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(value, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, color = GoldLight, style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}

@Composable
private fun ApplicationsSwitch(open: Boolean, onToggle: () -> Unit) {
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (open) Color(0x1455A55F) else Color(0x14C4302B))
            .border(1.dp, if (open) Color(0x3355A55F) else Color(0x33C4302B), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (open) "استقبال الطلبات مفتوح" else "استقبال الطلبات مغلق",
                style = MaterialTheme.typography.bodyLarge,
                color = Ink,
            )
            Text(
                if (open) "يظهر نموذج طلب الالتحاق في شاشة الدخول"
                else "لا يمكن لأحد إرسال طلب الآن",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
        }
        MiniButton(if (open) "إغلاق" else "فتح", !open, onToggle)
    }
}

@Composable
private fun SectionTitle(text: String, count: Int) {
    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = Ink, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x14000000))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = Ink)
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun EmptyCard(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Paper)
            .border(1.dp, CardLine, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
private fun RequestCard(
    name: String,
    email: String,
    phone: String,
    city: String,
    level: String,
    note: String,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    val levelLabel = HalqaGroup.fromKey(level)?.arabic
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Paper)
            .border(1.dp, CardLine, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaqraatalNoorGreen),
                contentAlignment = Alignment.Center,
            ) {
                Text(name.takeLast(1), color = GoldLight, style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, color = Ink, fontWeight = FontWeight.SemiBold)
                if (levelLabel != null) {
                    Text(
                        "المستوى المرغوب: $levelLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = GoldDark,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        ContactLine("البريد", email)
        ContactLine("الهاتف", phone)
        ContactLine("المدينة", city)
        if (note.isNotBlank() && note != "null") ContactLine("ملاحظة", note)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Gold)
                    .clickable { onAccept() },
                contentAlignment = Alignment.Center,
            ) {
                Text("قبول", color = DeepGreen, style = MaterialTheme.typography.labelLarge)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x0F000000))
                    .clickable { onReject() },
                contentAlignment = Alignment.Center,
            ) {
                Text("رفض", color = LiveRed, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ContactLine(label: String, value: String) {
    if (value.isBlank() || value == "null") return
    Row(modifier = Modifier.padding(vertical = 1.dp)) {
        Text("$label: ", style = MaterialTheme.typography.bodySmall, color = Muted)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StudentCard(
    name: String,
    subtitle: String,
    banned: Boolean,
    groups: List<Pair<String, String>>,
    onBan: () -> Unit,
    onAssign: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Paper)
            .border(1.dp, CardLine, RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (banned) Color(0x33C4302B) else MaqraatalNoorGreen),
                contentAlignment = Alignment.Center,
            ) {
                Text(name.takeLast(1), color = if (banned) LiveRed else GoldLight, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyLarge, color = Ink)
                Text(
                    if (banned) "محظورة" else subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (banned) LiveRed else Muted,
                )
            }
            MiniButton(if (banned) "رفع الحظر" else "حظر", banned, onBan)
        }
        if (groups.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("نقل إلى مجموعة:", style = MaterialTheme.typography.bodySmall, color = Muted)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                groups.take(2).forEach { pair ->
                    MiniButton(pair.second, false) { onAssign(pair.first) }
                }
            }
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Gold)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = DeepGreen, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun GhostButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(Color(0x14000000))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = DeepGreen, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun MiniButton(text: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (primary) Gold else Color(0x0F000000))
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (primary) DeepGreen else Ink,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

@Composable
private fun InfoBanner(text: String, background: Color) {
    Box(
        modifier = Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .padding(10.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = Ink)
    }
}

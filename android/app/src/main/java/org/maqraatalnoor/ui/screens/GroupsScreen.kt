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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.maqraatalnoor.data.remote.HalqaGroup
import org.maqraatalnoor.data.remote.RemoteMember
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
import org.maqraatalnoor.ui.viewmodels.GroupsViewModel

/** مجموعة واحدة تحتها مسارات تأسيس وتجويد وحفظ؛ الإجازة تخرّج مستقل. */
@Composable
fun GroupsScreen(
    onBack: () -> Unit,
    viewModel: GroupsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    var selectedGroup by remember(state.studyGroups) { mutableStateOf(state.studyGroups.firstOrNull()?.id.orEmpty()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        MaqraatalNoorTopBar("مجموعات المقرأة", "كل مجموعة تضم التأسيس والتجويد والحفظ", onBack)
        Column(modifier = Modifier.padding(16.dp)) {
            if (state.loading) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GoldDark)
                }
            }
            state.error?.let { Text(it, color = LiveRed, style = MaterialTheme.typography.bodyMedium) }
            state.notice?.let {
                Text(it, color = GoldDark, style = MaterialTheme.typography.bodyMedium)
            }

            if (state.studyGroups.isEmpty() && !state.loading) {
                Text(
                    if (viewModel.canEdit) "لا توجد مجموعات بعد — أنشئي مجموعة من لوحة الإشراف." else "لم تُسند إليك مجموعة بعد.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
            }

            state.studyGroups.forEach { group ->
                StudyGroupCard(group.name, group.studentsByLevel)
                Spacer(Modifier.height(10.dp))
            }

            if ((viewModel.canEdit || viewModel.canSuggest) && state.students.isNotEmpty() && state.studyGroups.isNotEmpty()) {
                Text(if (viewModel.canEdit) "إسناد الطالبات" else "اقتراح انتقال طالبة", style = MaterialTheme.typography.titleMedium, color = Ink, fontWeight = FontWeight.Bold)
                Text(if (viewModel.canEdit) "اختاري المجموعة ثم المستوى للطالبة." else "اقتراحاتك تصل إلى المشرفة لاعتمادها.", style = MaterialTheme.typography.bodySmall, color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    state.studyGroups.forEach { group ->
                        ChoiceChip(group.name, selectedGroup == group.id) { selectedGroup = group.id }
                    }
                }
                state.students.forEach { student ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Paper).padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(student.name, modifier = Modifier.weight(1f), color = Ink, fontWeight = FontWeight.SemiBold)
                            if (student.graduated) {
                                Text("مجازة", color = GoldDark, style = MaterialTheme.typography.labelMedium)
                            } else if (viewModel.canEdit) {
                                ChoiceChip("منح الإجازة", false) { viewModel.graduate(student, true) }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                            HalqaGroup.entries.forEach { level ->
                                ChoiceChip(level.arabic, false) {
                                    if (viewModel.canEdit) viewModel.assign(student.userId, selectedGroup, level)
                                    else viewModel.propose(student.userId, selectedGroup, level)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0x14000000))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Text("رجوع", color = DeepGreen, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StudyGroupCard(name: String, studentsByLevel: Map<String, List<String>>) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Paper)
        .border(1.dp, CardLine, RoundedCornerShape(18.dp)).padding(14.dp)) {
        Text(name, style = MaterialTheme.typography.titleLarge, color = Ink, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        HalqaGroup.entries.forEach { level ->
            val students = studentsByLevel[level.key].orEmpty()
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                Text(level.arabic, modifier = Modifier.width(92.dp), color = GoldDark, style = MaterialTheme.typography.labelLarge)
                Text("${students.size} طالبات", modifier = Modifier.width(70.dp), color = Muted, style = MaterialTheme.typography.bodySmall)
                Text(students.joinToString("، ").ifBlank { "لا توجد طالبات مسندات" }, color = Ink, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(18.dp)).background(if (selected) Gold else Color(0x12000000))
        .clickable { onClick() }.padding(horizontal = 11.dp, vertical = 7.dp)) {
        Text(text, color = if (selected) DeepGreen else Ink, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

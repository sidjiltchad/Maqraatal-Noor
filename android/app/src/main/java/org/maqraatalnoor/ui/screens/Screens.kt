package org.maqraatalnoor.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import org.maqraatalnoor.ui.theme.Paper
import org.maqraatalnoor.ui.theme.Ink
import org.maqraatalnoor.ui.theme.MaqraatalNoorGreen
import org.maqraatalnoor.ui.theme.HighlightGold
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.maqraatalnoor.R
import org.maqraatalnoor.data.DemoRepository
import org.maqraatalnoor.ui.components.InitialAvatar
import org.maqraatalnoor.ui.theme.CardLine
import org.maqraatalnoor.data.Level
import org.maqraatalnoor.data.Role
import org.maqraatalnoor.ui.components.ClassCard
import org.maqraatalnoor.ui.components.GoldButton
import org.maqraatalnoor.ui.components.MaqraatalNoorCard
import org.maqraatalnoor.ui.components.MaqraatalNoorTopBar
import org.maqraatalnoor.ui.components.SectionTitle
import org.maqraatalnoor.ui.theme.Cream
import org.maqraatalnoor.ui.theme.DeepGreen
import org.maqraatalnoor.ui.theme.Gold
import org.maqraatalnoor.ui.theme.GoldDark
import org.maqraatalnoor.ui.theme.GoldLight
import org.maqraatalnoor.ui.theme.Muted
import org.maqraatalnoor.ui.viewmodels.ClassesViewModel
import org.maqraatalnoor.ui.viewmodels.HomeRemoteViewModel
import org.maqraatalnoor.ui.viewmodels.HomeViewModel
import androidx.compose.foundation.BorderStroke

/** Screen 1 — entry. The supplied official logo carries the name, so no duplicate title. */
@Composable
fun SplashScreen(
    onEnterAs: (Role) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepGreen)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Image(
            painter = painterResource(R.drawable.logo_maqraatalnoor),
            contentDescription = "شعار مقرأة النور",
            modifier = Modifier.size(260.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text("حلقات قرآنية للنساء", color = GoldLight, style = MaterialTheme.typography.titleMedium)
        Text("إقراء • حفظ • تجويد", color = Cream, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        Text(
            "خيركم من تعلّم القرآن وعلّمه",
            color = Cream,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        GoldButton(
            text = "دخول الطالبة",
            onClick = { onEnterAs(Role.STUDENT) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { onEnterAs(Role.TEACHER) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(26.dp),
            border = BorderStroke(1.dp, Gold),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldLight),
        ) {
            Text("دخول المعلمة", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(24.dp))
        Text("الدخول بدعوة فقط • لا تسجيل عام", color = Cream, style = MaterialTheme.typography.bodyMedium)
        Text(
            "المصحف يعمل دون إنترنت بعد تنزيله",
            color = Color(0xFFC8D2C3),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Screen 2 — الرئيسية: بطاقة الحلقة، أرقام الأسبوع، والأقسام. */
@Composable
fun HomeScreen(
    role: Role,
    displayName: String,
    roleLabel: String = "",
    isStaff: Boolean,
    isSupervisor: Boolean = false,
    onNavigateMushaf: () -> Unit,
    onNavigateChats: () -> Unit = {},
    onNavigateSettings: () -> Unit = {},
    onNavigateGroups: () -> Unit = {},
    onNavigateAdmin: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(),
    homeRemote: HomeRemoteViewModel = viewModel(),
) {
    val live by homeRemote.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
            MaqraatalNoorTopBar("السلام عليكم، $displayName", roleLabel)

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            SectionTitle("مجموعاتي الدراسية")
            Spacer(Modifier.height(8.dp))
            if (live.groups.isEmpty()) {
                MaqraatalNoorCard(Modifier.fillMaxWidth()) {
                    Text("لا توجد مجموعات دراسية بعد", color = DeepGreen, style = MaterialTheme.typography.titleMedium)
                    Text("ستظهر المجموعات هنا بعد إنشائها وإسناد الطالبات إليها.", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                live.groups.take(3).forEach { group ->
                    MaqraatalNoorCard(Modifier.fillMaxWidth().clickable(onClick = onNavigateGroups)) {
                        Text(group.name, color = DeepGreen, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("${group.total} طالبة • التأسيس ${group.foundation} • التجويد ${group.tajweed} • الحفظ ${group.hifz}", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            SectionTitle("ملخص هذا الأسبوع")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(number = live.sessionsThisWeek.toString(), label = "جلسات", modifier = Modifier.weight(1f))
                StatCard(number = live.myAttendance.toString(), label = "حضور", modifier = Modifier.weight(1f))
                StatCard(number = live.averageTajweed, label = "تقييم التجويد", modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle(when {
                isSupervisor -> "مساحة الإشراف والإقراء"
                isStaff -> "مساحة المقرئة"
                else -> "مساحة الطالبة"
            })
            Spacer(Modifier.height(8.dp))
            if (isSupervisor) {
                SectionTile("إدارة المقرأة", "القبول والمجموعات ومتابعة الطالبات", "🛡", Modifier.fillMaxWidth(), onNavigateAdmin)
                Spacer(Modifier.height(8.dp))
            } else if (isStaff) {
                Spacer(Modifier.height(8.dp))
                SectionTile("مجموعاتي", "الطالبات والمستويات المسندة إليك", "👥", Modifier.fillMaxWidth(), onNavigateGroups)
            } else {
                SectionTile("مجموعتي ومستواي", "التأسيس، التجويد، الحفظ", "🎓", Modifier.fillMaxWidth(), onNavigateGroups)
            }

            SectionTitle("الأقسام")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTile("المحادثات", "المجموعات والخاصة", "💬", Modifier.weight(1f), onNavigateChats)
                SectionTile("المصحف", "القراءة والتجويد", "📖", Modifier.weight(1f), onNavigateMushaf)
            }
            Spacer(Modifier.height(10.dp))
            SectionTile("حسابي", "الإعدادات والملف الشخصي", "⚙", Modifier.fillMaxWidth(), onNavigateSettings)
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun SectionTile(
    title: String,
    subtitle: String,
    symbol: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Paper)
            .border(1.dp, CardLine, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaqraatalNoorGreen),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when (symbol) {
                    "💬" -> Icons.AutoMirrored.Filled.Chat
                    "📖" -> Icons.AutoMirrored.Filled.MenuBook
                    "⚙" -> Icons.Filled.Settings
                    "🛡" -> Icons.Filled.AdminPanelSettings
                    "👥" -> Icons.Filled.Groups
                    else -> Icons.Filled.School
                },
                contentDescription = title,
                tint = GoldLight,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Ink, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1)
    }
}

@Composable
private fun StatCard(number: String, label: String, modifier: Modifier = Modifier) {
    MaqraatalNoorCard(modifier) {
        Text(
            number,
            style = MaterialTheme.typography.headlineMedium,
            color = GoldDark,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun QuickAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(18.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = DeepGreen)
    }
}

/** Screen 3 — classes and levels. */
@Composable
fun ClassesScreen(
    halqaName: String,
    teachersLabel: String,
    membersCount: Int,
    isStaff: Boolean,
    onOpenLive: (String) -> Unit,
    viewModel: ClassesViewModel = viewModel(),
) {
    val student = viewModel.student
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        MaqraatalNoorTopBar("حلقتي ومستواي", "$halqaName • $teachersLabel")
        Column(modifier = Modifier.padding(16.dp)) {
            SectionTitle("مستواك الحالي")
            Spacer(Modifier.height(12.dp))
            LevelStepper(current = student.level)
            Spacer(Modifier.height(8.dp))
            Text(
                "أنت هنا: ${student.level.arabicName} • ${(student.levelProgress * 100).toInt()}٪ من المستوى",
                style = MaterialTheme.typography.bodyMedium,
                color = GoldDark,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start,
            )
            Spacer(Modifier.height(20.dp))
            ClassCard(
                classGroup = org.maqraatalnoor.data.ClassGroup(
                    id = "h-main",
                    name = halqaName,
                    level = student.level,
                    teachers = listOf(
                        org.maqraatalnoor.data.Teacher("t-main", teachersLabel, "الحلقة"),
                    ),
                    days = "الأحد والثلاثاء والخميس",
                    time = "17:00",
                    surah = "سورة الكهف",
                    enrolled = membersCount,
                    capacity = 30,
                    progress = student.levelProgress,
                ),
                actionLabel = "دخول الحلقة",
                onAction = { onOpenLive("h-main") },
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "مستوى الإجازة يفتح بعد إتمام الحفظ والتجويد، بالسند مع المشرفة العامة.",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
            )
            Spacer(Modifier.height(20.dp))
            SectionTitle("معلمات الحلقة")
            Spacer(Modifier.height(8.dp))
            MaqraatalNoorCard {
                DemoRepository.teachers.forEachIndexed { index, teacher ->
                    if (index > 0) {
                        androidx.compose.material3.HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            thickness = 0.6.dp,
                            color = CardLine,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        InitialAvatar(teacher.name.takeLast(1), size = 38, active = true)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                teacher.name,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                teacher.specialty,
                                style = MaterialTheme.typography.bodySmall,
                                color = Muted,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LevelStepper(current: Level) {
    val levels = Level.entries.sortedBy { it.order }
    val lineColor = Color(0xFFE4D9BC)
    val currentIndex = levels.indexOf(current)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                // Circles are 40.dp tall, so their centre sits at 20.dp.
                val y = 20.dp.toPx()
                val step = size.width / levels.size
                val firstCenter = if (layoutDirection == LayoutDirection.Rtl) {
                    size.width - step / 2f
                } else {
                    step / 2f
                }
                val direction = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
                val lastCenter = firstCenter + direction * step * (levels.size - 1)
                drawLine(
                    color = lineColor,
                    start = Offset(firstCenter, y),
                    end = Offset(lastCenter, y),
                    strokeWidth = 3.dp.toPx(),
                )
                val reachedCenter = firstCenter + direction * step * currentIndex
                drawLine(
                    color = Gold,
                    start = Offset(firstCenter, y),
                    end = Offset(reachedCenter, y),
                    strokeWidth = 3.dp.toPx(),
                )
            },
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Top,
    ) {
        // RTL: first level appears at the right in the design; Row in RTL
        // layout already orders children right-to-left.
        levels.forEach { level ->
            val done = level.order < current.order
            val isCurrent = level == current
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (done || isCurrent) Gold else Color.White)
                        .border(1.dp, if (done || isCurrent) Gold else lineColor, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = DeepGreen,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        Text(
                            (level.order + 1).toString(),
                            color = if (isCurrent) DeepGreen else Muted,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    level.arabicName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isCurrent) GoldDark else Muted,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

/** بطاقة مجموعة: الاسم + المستويات الثلاثة بعدد الطالبات في كل مستوى. */
@Composable
private fun GroupSummaryCard(
    name: String,
    foundation: Int,
    tajweed: Int,
    hifz: Int,
    onOpen: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Paper)
            .border(1.dp, CardLine, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(DeepGreen),
                contentAlignment = Alignment.Center,
            ) {
                Text("م", color = GoldLight, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${foundation + tajweed + hifz} طالبة",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LevelPill("التأسيس", foundation, Modifier.weight(1f), onOpen)
            LevelPill("التجويد", tajweed, Modifier.weight(1f), onOpen)
            LevelPill("الحفظ", hifz, Modifier.weight(1f), onOpen)
        }
    }
}

@Composable
private fun LevelPill(
    label: String,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (count > 0) HighlightGold else Color(0x0D000000))
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            count.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = if (count > 0) DeepGreen else Muted,
            fontWeight = FontWeight.Bold,
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = if (count > 0) DeepGreen else Muted,
            maxLines = 1,
        )
    }
}

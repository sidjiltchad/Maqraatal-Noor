package org.maqraatalnoor.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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

/** Screen 2 — student home. */
@Composable
fun HomeScreen(
    role: Role,
    onNavigateLive: (String) -> Unit,
    onNavigateClasses: () -> Unit,
    onNavigateMushaf: () -> Unit,
    onNavigatePrivate: (String) -> Unit,
    onNavigateTeacher: (String) -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val halqa = viewModel.halqa
    val weekly = viewModel.weekly
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        MaqraatalNoorTopBar("السلام عليكم، أمينة", "مستوى التجويد • فصل المتقدمات")
        Column(modifier = Modifier.padding(16.dp)) {
            // Next halqa card (deep green, as in the design).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(DeepGreen)
                    .padding(16.dp),
            ) {
                Column {
                    Text("الحلقة القادمة", color = GoldLight, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "الحلقة العامة • مع المعلمات أم سارة وأم مريم",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "${halqa.time} • ${halqa.surah}",
                        color = Cream,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GoldButton(text = "انضمي عند الموعد", onClick = { onNavigateLive(halqa.classId) })
                        Spacer(Modifier.weight(1f))
                        Text("يتبقى 02:14:30", color = GoldLight, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            SectionTitle("تقدمك هذا الأسبوع")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(number = weekly.halqas.toString(), label = "حلقات", modifier = Modifier.weight(1f))
                StatCard(number = weekly.pagesRead.toString(), label = "صفحة قراءة", modifier = Modifier.weight(1f))
                StatCard(number = "${weekly.tajweedScore}/5", label = "تقييم التجويد", modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(20.dp))
            SectionTitle("حلقتي")
            Spacer(Modifier.height(8.dp))
            viewModel.classes.take(2).forEach { classGroup ->
                ClassCard(
                    classGroup = classGroup,
                    actionLabel = "دخول الفصل",
                    onAction = { onNavigateClasses() },
                )
                Spacer(Modifier.height(12.dp))
            }
            if (role == Role.TEACHER) {
                GoldButton(
                    text = "تحكم المعلمة — الحلقة العامة",
                    onClick = { onNavigateTeacher("c-tajweed") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
            }
            SectionTitle("وصول سريع")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickAction("المصحف", Modifier.weight(1f), onNavigateMushaf)
                QuickAction("واجباتي (2)", Modifier.weight(1f), onNavigateClasses)
                QuickAction("تسميعي الصوتي", Modifier.weight(1f)) { onNavigatePrivate("s-mariam") }
            }
            Spacer(Modifier.height(16.dp))
        }
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
    onOpenLive: (String) -> Unit,
    onOpenPrivate: (String) -> Unit,
    viewModel: ClassesViewModel = viewModel(),
) {
    val student = viewModel.student
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        MaqraatalNoorTopBar("حلقتي ومستواي", "حلقة واحدة • عدة معلمات • مسارك من التأسيس إلى الإجازة")
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
            viewModel.classes.forEach { classGroup ->
                val locked = classGroup.locked
                ClassCard(
                    classGroup = classGroup,
                    actionLabel = when {
                        locked -> "مقفل"
                        classGroup.isPrivate -> "موعدي"
                        else -> "دخول الحلقة"
                    },
                    onAction = {
                        when {
                            locked -> Unit
                            classGroup.isPrivate -> onOpenPrivate("s-mariam")
                            else -> onOpenLive(classGroup.id)
                        }
                    },
                    actionEnabled = !locked,
                )
                Spacer(Modifier.height(12.dp))
            }
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

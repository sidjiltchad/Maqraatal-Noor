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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.maqraatalnoor.ui.components.GoldButton
import org.maqraatalnoor.ui.components.InitialAvatar
import org.maqraatalnoor.ui.components.MaqraatalNoorCard
import org.maqraatalnoor.ui.components.MaqraatalNoorTopBar
import org.maqraatalnoor.ui.components.SectionTitle
import org.maqraatalnoor.ui.theme.CardLine
import org.maqraatalnoor.ui.theme.DeepGreen
import org.maqraatalnoor.ui.theme.DeepGreenDark
import org.maqraatalnoor.ui.theme.FeedbackSuccess
import org.maqraatalnoor.ui.theme.FeedbackSuccessSurface
import org.maqraatalnoor.ui.theme.Gold
import org.maqraatalnoor.ui.theme.GoldLight
import org.maqraatalnoor.ui.theme.HighlightGold
import org.maqraatalnoor.ui.theme.Ink
import org.maqraatalnoor.ui.theme.LiveRed
import org.maqraatalnoor.ui.theme.Muted
import org.maqraatalnoor.ui.theme.Sage

private val HeroShape = RoundedCornerShape(24.dp)

/**
 * "حسابي" hub: identity and status for every signed-in account, plus the owner
 * entry point. The owner dashboard link is rendered only when the server-side
 * `is_owner()` check resolved true.
 */
@Composable
fun AccountScreen(
    name: String,
    email: String,
    roleLabel: String,
    isOwner: Boolean,
    statusLabel: String,
    halqaName: String,
    levelLabel: String,
    onOpenOwnerDashboard: () -> Unit,
    onOpenSettings: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        MaqraatalNoorTopBar("حسابي", "$name • $roleLabel", onBack)

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            AccountHero(
                name = name,
                email = email,
                roleLabel = roleLabel,
                isOwner = isOwner,
                statusLabel = statusLabel,
            )

            Spacer(Modifier.height(20.dp))
            SectionTitle("ملف الحساب")
            Spacer(Modifier.height(8.dp))
            MaqraatalNoorCard {
                AccountInfoRow("الاسم", name)
                HorizontalDivider(thickness = 0.6.dp, color = CardLine)
                AccountInfoRow("البريد", email, ltr = true)
                HorizontalDivider(thickness = 0.6.dp, color = CardLine)
                AccountInfoRow("الدور", roleLabel)
                HorizontalDivider(thickness = 0.6.dp, color = CardLine)
                AccountInfoRow("الحلقة", halqaName)
                HorizontalDivider(thickness = 0.6.dp, color = CardLine)
                AccountInfoRow("المستوى", levelLabel)
            }

            if (isOwner) {
                Spacer(Modifier.height(20.dp))
                SectionTitle("إدارة المقرأة")
                Spacer(Modifier.height(8.dp))
                OwnerCallout(onOpenOwnerDashboard)
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle("الإعدادات")
            Spacer(Modifier.height(8.dp))
            MaqraatalNoorCard(Modifier.clickable(onClick = onOpenSettings)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Sage),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null,
                            tint = DeepGreen,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "الإعدادات والخط",
                            style = MaterialTheme.typography.titleMedium,
                            color = Ink,
                        )
                        Text(
                            "الخط، متابعة المعلمة، ومعلومات التطبيق",
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "فتح الإعدادات",
                        tint = Muted,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp),
            ) {
                Text("تسجيل الخروج", color = LiveRed, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun AccountHero(
    name: String,
    email: String,
    roleLabel: String,
    isOwner: Boolean,
    statusLabel: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HeroShape)
            .background(Brush.verticalGradient(listOf(DeepGreen, DeepGreenDark)))
            .border(1.dp, Gold.copy(alpha = 0.55f), HeroShape)
            .padding(18.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InitialAvatar(
                    initial = name.trim().takeLast(1).ifBlank { "ع" },
                    size = 64,
                    active = true,
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        name,
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        email.ifBlank { "—" },
                        color = GoldLight,
                        style = MaterialTheme.typography.bodySmall.copy(
                            textDirection = TextDirection.Ltr,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroPill(
                    text = if (isOwner) "مالكة المقرأة" else roleLabel,
                    icon = if (isOwner) Icons.Filled.Verified else null,
                )
                HeroStatusPill(statusLabel)
            }
        }
    }
}

@Composable
private fun HeroPill(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Gold)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = DeepGreen,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text,
            color = DeepGreen,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun HeroStatusPill(statusLabel: String) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(FeedbackSuccessSurface)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(FeedbackSuccess),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "الحساب $statusLabel",
            color = FeedbackSuccess,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun AccountInfoRow(label: String, value: String, ltr: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
            modifier = Modifier.width(76.dp),
        )
        Text(
            value.ifBlank { "—" },
            style = if (ltr) {
                MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr)
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = Ink,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Start,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OwnerCallout(onOpenOwnerDashboard: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(HighlightGold)
            .border(1.dp, Gold, RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DeepGreen),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.AdminPanelSettings,
                        contentDescription = null,
                        tint = GoldLight,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "لوحة المالكة",
                        style = MaterialTheme.typography.titleMedium,
                        color = DeepGreen,
                    )
                    Text(
                        "خاصة بحساب المالكة فقط",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "أنشئي حساب المشرفة العامة مباشرة، وراجعي الصلاحيات المفروضة من الخادم.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink,
            )
            Spacer(Modifier.height(12.dp))
            GoldButton(
                text = "فتح لوحة المالكة",
                onClick = onOpenOwnerDashboard,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

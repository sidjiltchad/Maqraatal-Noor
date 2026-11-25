package org.maqraatalnoor.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.maqraatalnoor.R
import org.maqraatalnoor.data.ClassGroup
import org.maqraatalnoor.ui.theme.CardLine
import org.maqraatalnoor.ui.theme.DeepGreen
import org.maqraatalnoor.ui.theme.Gold
import org.maqraatalnoor.ui.theme.GoldDark
import org.maqraatalnoor.ui.theme.GoldLight
import org.maqraatalnoor.ui.theme.MaqraatalNoorGreen
import org.maqraatalnoor.ui.theme.Muted
import org.maqraatalnoor.ui.theme.Paper

/** هالة زخرفية هادئة مستوحاة من الأقواس والنقوش الهندسية في هوية المقرأة. */
@Composable
fun MaqraatalNoorPattern(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val step = 112.dp.toPx()
        val radius = 15.dp.toPx()
        val stroke = 1.dp.toPx()
        val tint = GoldDark.copy(alpha = 0.045f)
        var y = 40.dp.toPx()
        while (y < size.height) {
            var x = 30.dp.toPx() + if ((y / step).toInt() % 2 == 0) 0f else step / 2f
            while (x < size.width) {
                val center = Offset(x, y)
                drawCircle(tint, radius, center, style = Stroke(stroke))
                drawCircle(tint, radius * 0.45f, center, style = Stroke(stroke))
                drawLine(tint, Offset(x - radius, y), Offset(x + radius, y), stroke)
                drawLine(tint, Offset(x, y - radius), Offset(x, y + radius), stroke)
                drawLine(tint, Offset(x - radius * .7f, y - radius * .7f), Offset(x + radius * .7f, y + radius * .7f), stroke)
                drawLine(tint, Offset(x + radius * .7f, y - radius * .7f), Offset(x - radius * .7f, y + radius * .7f), stroke)
                x += step
            }
            y += step
        }
    }
}

/**
 * Deep-green header with the supplied official logo (small, in its white
 * disc) and the screen title. The header is drawn edge-to-edge: it extends
 * behind the status bar and adds the status-bar inset as padding, so no
 * empty band shows above it. The logo is never redrawn or recoloured.
 */
@Composable
fun MaqraatalNoorTopBar(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DeepGreen)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Color.White)
            }
            Spacer(Modifier.width(4.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                title,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = GoldLight,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Image(
            painter = painterResource(R.drawable.logo_maqraatalnoor),
            contentDescription = "شعار مقرأة النور",
            modifier = Modifier.size(52.dp),
        )
    }
}

/** Initial-circle avatar. No photos of people anywhere in the app. */
@Composable
fun InitialAvatar(
    initial: String,
    size: Int = 48,
    active: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(if (active) MaqraatalNoorGreen else Color(0xFFE7DDC3))
            .border(2.dp, Gold, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initial,
            color = if (active) GoldLight else Muted,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun GoldButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(26.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = DeepGreen),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun GoldOutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(26.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldDark),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            width = 1.dp,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun MaqraatalNoorCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Paper),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun MaqraatalNoorProgress(progress: Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp)),
        color = Gold,
        trackColor = Color(0xFFE8E2D2),
    )
}

@Composable
fun ClassCard(
    classGroup: ClassGroup,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    actionEnabled: Boolean = true,
) {
    MaqraatalNoorCard(modifier) {
        Text(
            classGroup.name,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "المعلمات ${classGroup.teachersLabel} • ${classGroup.enrolled} عضوة",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "${classGroup.days} • ${classGroup.time} • ${classGroup.surah}",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PillAction(actionLabel, actionEnabled, onAction)
            Spacer(Modifier.width(14.dp))
            Box(Modifier.weight(1f)) { MaqraatalNoorProgress(classGroup.progress) }
        }
    }
}

/** Compact gold pill used on class cards, sized as in the design mock-ups. */
@Composable
private fun PillAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (enabled) Gold else Color(0xFFE2D9C2))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) DeepGreen else Muted,
            maxLines = 1,
        )
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Start,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun RowEnd(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        content = content,
    )
}

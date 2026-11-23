package org.maqraatalnoor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.maqraatalnoor.domain.MushafLogic
import org.maqraatalnoor.ui.components.MaqraatalNoorTopBar
import org.maqraatalnoor.ui.theme.DeepGreen
import org.maqraatalnoor.ui.theme.Gold
import org.maqraatalnoor.ui.theme.GoldDark
import org.maqraatalnoor.ui.theme.HighlightGold
import org.maqraatalnoor.ui.theme.Ink
import org.maqraatalnoor.ui.theme.Muted
import org.maqraatalnoor.ui.theme.Paper
import org.maqraatalnoor.ui.theme.QuranFamily
import org.maqraatalnoor.ui.theme.Sage
import org.maqraatalnoor.ui.viewmodels.MushafViewModel

/**
 * Screen 5 — Mushaf reader. Sample only: Surah Al-Fatiha, one page.
 * Quran text uses the bundled Noto Naskh family, never the interface font.
 */
@Composable
fun MushafScreen(
    viewModel: MushafViewModel = viewModel(),
) {
    val pageNumber by viewModel.page.collectAsState()
    val highlighted by viewModel.highlightedAyah.collectAsState()
    val tajweedOn by viewModel.tajweedColours.collectAsState()
    val bookmarkAyah by viewModel.bookmarkAyah.collectAsState()
    val page = viewModel.currentPage()
    val totalPages = viewModel.totalPages

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .verticalScroll(rememberScrollState()),
    ) {
        MaqraatalNoorTopBar(
            "سورة ${page.surah}",
            "صفحة $pageNumber • رواية حفص • مصحف المدينة",
        )
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                "صفحة $pageNumber من 604 في المصحف الكامل — هذه النسخة تحوي صفحة العينة فقط (الفاتحة).",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            // Ornamental divider.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Gold),
            )
            Spacer(Modifier.height(20.dp))

            page.ayahs.forEach { ayah ->
                val isHighlighted = MushafLogic.isHighlighted(ayah.number, highlighted)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isHighlighted) HighlightGold else Paper)
                        .clickable { viewModel.selectAyah(ayah.number) }
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .border(1.dp, GoldDark, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                ayah.number.toString(),
                                color = GoldDark,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        Text(
                            ayah.text,
                            fontFamily = QuranFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp,
                            color = Ink,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "الآية المظللة هي موضع القراءة الحالي — اضغطي أي آية لتظليلها.",
                style = MaterialTheme.typography.bodyMedium,
                color = GoldDark,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (tajweedOn) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "ألوان التجويد: تُطبَّق عند استيراد نص التجويد الكامل؛ نص العينة يعرض بلا تلوين للأحكام.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(20.dp))

            Row {
                MushafNavButton("الصفحة السابقة", Modifier.weight(1f), MushafLogic.hasPrevious(pageNumber)) {
                    viewModel.previousPage()
                }
                Spacer(Modifier.size(8.dp))
                MushafNavButton("علامة", Modifier.weight(1f), true) { viewModel.saveBookmark() }
                Spacer(Modifier.size(8.dp))
                MushafNavButton("ألوان التجويد", Modifier.weight(1f), true) { viewModel.toggleTajweedColours() }
                Spacer(Modifier.size(8.dp))
                MushafNavButton("الصفحة التالية", Modifier.weight(1f), MushafLogic.hasNext(pageNumber, totalPages)) {
                    viewModel.nextPage()
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Sage)
                    .padding(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "يعمل دون إنترنت • العلامة المحفوظة: آية $bookmarkAyah • تكبير الخط من الإعدادات",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DeepGreen,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MushafNavButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    if (enabled) {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(48.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = DeepGreen),
        ) {
            Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    } else {
        Button(
            onClick = {},
            enabled = false,
            modifier = modifier.height(48.dp),
            shape = RoundedCornerShape(24.dp),
        ) {
            Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

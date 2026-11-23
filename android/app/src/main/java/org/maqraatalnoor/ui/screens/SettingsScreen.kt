package org.maqraatalnoor.ui.screens

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.maqraatalnoor.fonts.FontLoader
import org.maqraatalnoor.ui.components.GoldButton
import org.maqraatalnoor.ui.components.MaqraatalNoorCard
import org.maqraatalnoor.ui.components.MaqraatalNoorTopBar
import org.maqraatalnoor.ui.components.SectionTitle
import org.maqraatalnoor.ui.theme.DeepGreen
import org.maqraatalnoor.ui.theme.GoldDark
import org.maqraatalnoor.ui.theme.Muted
import org.maqraatalnoor.ui.viewmodels.SettingsViewModel

/**
 * Screen 8 — account and settings, including the font screen.
 *
 * Thamaniya (خط ثمانية) is NEVER bundled, downloaded, requested, or sent
 * by this app. The screen explains manual import only: the user downloads
 * the font from the official source on her own device and imports the OTF
 * file here. Whether the font may be embedded in an Android build is NOT
 * decided in this build — the full licence text that arrives with the
 * download email has not been read, and the official FAQ is contradictory
 * on installation on iOS/Android. Until that licence text is read, the
 * compatible model is manual import by the user on her own device only.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val imported by viewModel.imported.collectAsState()
    val selectedFontId by viewModel.selectedFontId.collectAsState()
    val followTeacher by viewModel.followTeacher.collectAsState()
    var importResult by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val displayName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else null
        } ?: "font.otf"
        val ok = viewModel.importFont(uri, displayName)
        importResult = if (ok) "استُورد «$displayName» وطُبِّق على الواجهة." else "تعذّر استيراد الملف؛ تأكدي أنه ملف خط OTF أو TTF صالح."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        MaqraatalNoorTopBar("حسابي والإعدادات", "أمينة • الحلقة العامة • الدخول بدعوة فقط")
        Column(modifier = Modifier.padding(16.dp)) {
            SectionTitle("الخط")
            Spacer(Modifier.height(8.dp))
            MaqraatalNoorCard {
                Text(
                    "الخط الافتراضي للواجهة هو IBM Plex Sans Arabic. ويمكنك استيراد خط ثمانية يدويا من جهازك، وسيُطبَّق على الواجهة فقط، أما نص المصحف فيبقى دائما بخط النسخ العثماني.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "خط ثمانية غير مضمَّن في التطبيق، ولا يُنزَّل منه شيء، ولا يُرسَل ملفه إلى أحد. التنزيل من الصفحة الرسمية وحدها (ask.thmanyah.com) ببريدك الإلكتروني، ثم استوردي ملف OTF هنا. ترخيص الخط يمنع إعادة توزيع ملفاته ورفعها أو استضافتها خارج المصدر الرسمي، ولم يُقرأ نص الترخيص الكامل بعد، فلا يُحسم تضمين الخط في التطبيق قبل قراءته.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
                Spacer(Modifier.height(12.dp))
                FontRow(
                    name = "IBM Plex Sans Arabic (الافتراضي)",
                    selected = selectedFontId == FontLoader.DEFAULT_FONT_ID,
                    onSelect = { viewModel.selectFont(FontLoader.DEFAULT_FONT_ID) },
                )
                imported.forEach { font ->
                    FontRow(
                        name = font.displayName,
                        selected = selectedFontId == font.id,
                        onSelect = { viewModel.selectFont(font.id) },
                        onDelete = { viewModel.deleteFont(font.id) },
                    )
                }
                Spacer(Modifier.height(12.dp))
                GoldButton(
                    text = "استيراد خط من جهازي (OTF)",
                    onClick = { picker.launch("*/*") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (importResult != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(importResult!!, style = MaterialTheme.typography.bodyMedium, color = GoldDark)
                }
            }
            Spacer(Modifier.height(20.dp))
            SectionTitle("متابعة المعلمة")
            Spacer(Modifier.height(8.dp))
            MaqraatalNoorCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "قلب الصفحات تلقائيا مع المعلمة أثناء الحلقة",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = followTeacher, onCheckedChange = { viewModel.setFollowTeacher(it) })
                }
            }
            Spacer(Modifier.height(20.dp))
            SectionTitle("عن التطبيق")
            Spacer(Modifier.height(8.dp))
            MaqraatalNoorCard {
                Text("مقرأة النور — Maqraatal-Noor", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("نسخة محلية 1.0 • بلا خادم • البيانات محفوظة على هذا الجهاز فقط", style = MaterialTheme.typography.bodyMedium, color = Muted)
                Spacer(Modifier.height(6.dp))
                Text("المصحف: صفحة عينة واحدة (الفاتحة)؛ المصحف الكامل يحتاج مصدر نص مفتوحا واستيرادا لاحقا.", style = MaterialTheme.typography.bodyMedium, color = Muted)
                Spacer(Modifier.height(6.dp))
                Text("المكالمات: حالة محاكاة محلية فقط في هذه النسخة؛ المكالمات الحقيقية تحتاج خادما وإشارات وحسابات.", style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = { /* Local build: no accounts server; sign-out only leaves the demo session. */ }, modifier = Modifier.fillMaxWidth()) {
                Text("تسجيل الخروج", color = DeepGreen)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FontRow(
    name: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) org.maqraatalnoor.ui.theme.Sage else org.maqraatalnoor.ui.theme.Paper)
            .clickable { onSelect() }
            .padding(vertical = 12.dp, horizontal = 12.dp),
    ) {
        // Radio-style marker: gold ring with an inner dot when selected.
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    color = if (selected) GoldDark else org.maqraatalnoor.ui.theme.CardLine,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(GoldDark),
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (selected) {
                Text("مُطبَّق على الواجهة", color = GoldDark, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (onDelete != null) {
            Text(
                "حذف",
                color = Muted,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onDelete() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}

package org.maqraatalnoor.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.maqraatalnoor.R
import org.maqraatalnoor.ui.components.GoldButton
import org.maqraatalnoor.ui.theme.Cream
import org.maqraatalnoor.ui.theme.DeepGreen
import org.maqraatalnoor.ui.theme.Gold
import org.maqraatalnoor.ui.theme.GoldLight
import org.maqraatalnoor.ui.theme.Muted
import org.maqraatalnoor.ui.theme.Paper

/**
 * شاشة الدخول الحقيقية: بريد وكلمة مرور إلى Supabase، والدخول بدعوة فقط.
 */
@Composable
fun LoginScreen(
    busy: Boolean,
    error: String?,
    onSignIn: (email: String, password: String) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepGreen)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        Image(
            painter = painterResource(R.drawable.logo_maqraatalnoor),
            contentDescription = "شعار مقرأة النور",
            modifier = Modifier.size(200.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "حلقات قرآنية للنساء",
            color = GoldLight,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "الدخول بدعوة فقط • لا تسجيل عام",
            color = Cream,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it.trim() },
            label = { Text("البريد الإلكتروني") },
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Gold,
                unfocusedBorderColor = Gold.copy(alpha = 0.5f),
                focusedLabelColor = GoldLight,
                unfocusedLabelColor = Cream.copy(alpha = 0.8f),
                cursorColor = Gold,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("كلمة المرور") },
            singleLine = true,
            enabled = !busy,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Gold,
                unfocusedBorderColor = Gold.copy(alpha = 0.5f),
                focusedLabelColor = GoldLight,
                unfocusedLabelColor = Cream.copy(alpha = 0.8f),
                cursorColor = Gold,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x33C4302B), RoundedCornerShape(10.dp))
                    .padding(10.dp),
            ) {
                Text(
                    error,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        if (busy) {
            CircularProgressIndicator(color = Gold, modifier = Modifier.size(36.dp))
        } else {
            GoldButton(
                text = "دخول",
                onClick = { onSignIn(email, password) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "إن وصلتك دعوة على بريدك، سجّلي الدخول بها ثم أدخلي كود الدعوة من الإعدادات.",
            color = Muted,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}

/** شاشة انتظار قصيرة أثناء التحقق من الجلسة المحفوظة. */
@Composable
fun BootScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(DeepGreen),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.logo_maqraatalnoor),
                contentDescription = null,
                modifier = Modifier.size(180.dp),
            )
            Spacer(Modifier.height(20.dp))
            CircularProgressIndicator(color = Paper, modifier = Modifier.size(32.dp))
        }
    }
}

/**
 * شاشة من دخلت بلا عضوية: تُدخل كود الدعوة فتنضم إلى الحلقة.
 */
@Composable
fun JoinHalqaScreen(
    name: String,
    busy: Boolean,
    error: String?,
    onRedeem: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    var code by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepGreen)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.logo_maqraatalnoor),
            contentDescription = null,
            modifier = Modifier.size(140.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "أهلا $name",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            "حسابك جاهز لكنه ليس في الحلقة بعد.\nأدخل كود الدعوة الذي وصلك من المعلمة.",
            color = Cream,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.trim().uppercase() },
            label = { Text("كود الدعوة") },
            singleLine = true,
            enabled = !busy,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Gold,
                unfocusedBorderColor = Gold.copy(alpha = 0.5f),
                focusedLabelColor = GoldLight,
                unfocusedLabelColor = Cream.copy(alpha = 0.8f),
                cursorColor = Gold,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(error, color = Color(0xFFF3B0AA), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(18.dp))
        if (busy) {
            CircularProgressIndicator(color = Gold, modifier = Modifier.size(34.dp))
        } else {
            GoldButton(
                text = "الانضمام إلى الحلقة",
                onClick = { onRedeem(code) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onSignOut) {
                Text("تسجيل الخروج", color = Cream)
            }
        }
    }
}
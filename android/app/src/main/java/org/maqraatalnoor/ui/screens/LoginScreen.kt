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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.maqraatalnoor.R
import org.maqraatalnoor.ui.components.GoldButton
import org.maqraatalnoor.ui.theme.CardLine
import org.maqraatalnoor.ui.theme.Cream
import org.maqraatalnoor.ui.theme.DeepGreen
import org.maqraatalnoor.ui.theme.Gold
import org.maqraatalnoor.ui.theme.GoldDark
import org.maqraatalnoor.ui.theme.GoldLight
import org.maqraatalnoor.ui.theme.Ink
import org.maqraatalnoor.ui.theme.MaqraatalNoorBackground
import org.maqraatalnoor.ui.theme.Muted
import org.maqraatalnoor.ui.theme.Paper

private val CardShape = RoundedCornerShape(20.dp)
private val FieldShape = RoundedCornerShape(14.dp)

private val BrandError = Color(0xFFA92F2F)
private val BrandErrorSurface = Color(0xFFFBEDED)
private val BrandSuccess = Color(0xFF2F6B4F)
private val BrandSuccessSurface = Color(0xFFEDF5EF)

private val EmailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

private val LevelOptions = listOf(
    "foundation" to "التأسيس",
    "tajweed" to "التجويد",
    "hifz" to "الحفظ",
)

private enum class AuthMessageTone { Error, Success, Info }

private fun isValidEmail(value: String): Boolean = EmailPattern.matches(value.trim())

private fun isValidPhone(value: String): Boolean {
    val digits = value.count { it.isDigit() }
    val hasOnlyPhoneCharacters = value.all { it.isDigit() || it in "+-() " }
    return hasOnlyPhoneCharacters && digits in 7..15
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Ink,
    unfocusedTextColor = Ink,
    focusedBorderColor = Gold,
    unfocusedBorderColor = Gold.copy(alpha = 0.5f),
    focusedLabelColor = DeepGreen,
    unfocusedLabelColor = Muted,
    cursorColor = DeepGreen,
)

@Composable
private fun loginFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Ink,
    unfocusedTextColor = Ink,
    disabledTextColor = Ink.copy(alpha = 0.75f),
    errorTextColor = Ink,
    focusedContainerColor = Paper,
    unfocusedContainerColor = Paper,
    disabledContainerColor = Paper,
    errorContainerColor = Paper,
    focusedBorderColor = GoldDark,
    unfocusedBorderColor = CardLine,
    disabledBorderColor = CardLine,
    errorBorderColor = BrandError,
    focusedLabelColor = DeepGreen,
    unfocusedLabelColor = Muted,
    disabledLabelColor = Muted,
    errorLabelColor = BrandError,
    cursorColor = DeepGreen,
    errorCursorColor = BrandError,
    focusedSupportingTextColor = Muted,
    unfocusedSupportingTextColor = Muted,
    disabledSupportingTextColor = Muted.copy(alpha = 0.7f),
    errorSupportingTextColor = BrandError,
)

/**
 * شاشة الدخول: تبويبان — تسجيل الدخول وطلب الالتحاق.
 *
 * النموذج قصير ومرحلي: الشعار والهوية في الأعلى، ثم تبويب واحد ظاهر،
 * ثم بطاقة النموذج. كل حقل له عنوان وتعليمات وخطأ محلي، والتبويبات
 * والأزرار تُعطَّل أثناء الإرسال مع إبقاء المدخلات.
 */
@Composable
fun LoginScreen(
    busy: Boolean,
    error: String?,
    joinStatus: String?,
    joinBusy: Boolean,
    onSignIn: (email: String, password: String) -> Unit,
    onSubmitJoin: (name: String, email: String, phone: String, city: String, note: String, level: String) -> Unit,
) {
    var tab by remember { mutableStateOf(0) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    var name by remember { mutableStateOf("") }
    var joinEmail by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var level by remember { mutableStateOf("tajweed") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var joinEmailError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var cityError by remember { mutableStateOf<String?>(null) }

    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val nameFocus = remember { FocusRequester() }
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val joinEmailFocus = remember { FocusRequester() }
    val phoneFocus = remember { FocusRequester() }
    val cityFocus = remember { FocusRequester() }
    val noteFocus = remember { FocusRequester() }

    val submitSignIn: () -> Unit = {
        if (!busy) {
            val emailProblem = when {
                email.isBlank() -> "أدخلي البريد الإلكتروني."
                !isValidEmail(email) -> "أدخلي بريدًا إلكترونيًا صحيحًا."
                else -> null
            }
            val passwordProblem = if (password.isBlank()) "أدخلي كلمة المرور." else null
            emailError = emailProblem
            passwordError = passwordProblem
            if (emailProblem == null && passwordProblem == null) {
                focusManager.clearFocus(force = true)
                onSignIn(email.trim(), password)
            } else if (emailProblem != null) {
                emailFocus.requestFocus()
            } else {
                passwordFocus.requestFocus()
            }
        }
    }

    val submitJoin: () -> Unit = {
        if (!joinBusy) {
            val nameProblem = if (name.isBlank()) "أدخلي الاسم الكامل." else null
            val emailProblem = when {
                joinEmail.isBlank() -> "أدخلي البريد الإلكتروني."
                !isValidEmail(joinEmail) -> "أدخلي بريدًا إلكترونيًا صحيحًا."
                else -> null
            }
            val phoneProblem = when {
                phone.isBlank() -> "أدخلي رقم الهاتف."
                !isValidPhone(phone) -> "أدخلي رقم هاتف صحيحًا (7 أرقام على الأقل)."
                else -> null
            }
            val cityProblem = if (city.isBlank()) "أدخلي المدينة." else null
            nameError = nameProblem
            joinEmailError = emailProblem
            phoneError = phoneProblem
            cityError = cityProblem
            if (nameProblem == null && emailProblem == null && phoneProblem == null && cityProblem == null) {
                focusManager.clearFocus(force = true)
                onSubmitJoin(name, joinEmail, phone, city, note, level)
            } else {
                when {
                    nameProblem != null -> nameFocus.requestFocus()
                    emailProblem != null -> joinEmailFocus.requestFocus()
                    phoneProblem != null -> phoneFocus.requestFocus()
                    cityProblem != null -> cityFocus.requestFocus()
                }
            }
        }
    }

    val selectTab: (Int) -> Unit = { next ->
        if (!busy && !joinBusy && next != tab) {
            tab = next
            emailError = null
            passwordError = null
            nameError = null
            joinEmailError = null
            phoneError = null
            cityError = null
            passwordVisible = false
            focusManager.clearFocus(force = true)
        }
    }

    LaunchedEffect(tab) {
        scrollState.scrollTo(0)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaqraatalNoorBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.logo_maqraatalnoor),
                contentDescription = "شعار مقرأة حكمة النور",
                modifier = Modifier.size(72.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "مقرأة حكمة النور",
                color = DeepGreen,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "حلقات قرآنية للنساء",
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))

            AuthTabs(
                selectedTab = tab,
                enabled = !busy && !joinBusy,
                onSelect = selectTab,
            )
            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .background(Paper)
                    .border(1.dp, CardLine, CardShape)
                    .padding(16.dp),
            ) {
                Text(
                    text = if (tab == 0) "تسجيل الدخول" else "طلب الالتحاق كطالبة",
                    color = DeepGreen,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (tab == 0) {
                        "للحسابات المعتمدة في المقرأة. الدخول بدعوة فقط."
                    } else {
                        "أرسلي بياناتك لمراجعة المشرفة العامة. لا يُنشأ حساب تلقائيًا."
                    },
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))

                if (tab == 0) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it.trim()
                            emailError = null
                        },
                        label = { Text("البريد الإلكتروني") },
                        singleLine = true,
                        enabled = !busy,
                        isError = emailError != null,
                        supportingText = {
                            Text(
                                text = emailError ?: "البريد المعتمد في المقرأة.",
                                color = if (emailError != null) BrandError else Muted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            textDirection = TextDirection.Ltr,
                            textAlign = TextAlign.Start,
                        ),
                        colors = loginFieldColors(),
                        shape = FieldShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(emailFocus),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            passwordError = null
                        },
                        label = { Text("كلمة المرور") },
                        singleLine = true,
                        enabled = !busy,
                        isError = passwordError != null,
                        supportingText = {
                            Text(
                                text = passwordError ?: "كلمة المرور حساسة لحالة الأحرف.",
                                color = if (passwordError != null) BrandError else Muted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { submitSignIn() }),
                        trailingIcon = {
                            IconButton(
                                onClick = { passwordVisible = !passwordVisible },
                                enabled = !busy,
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    imageVector = if (passwordVisible) {
                                        Icons.Filled.VisibilityOff
                                    } else {
                                        Icons.Filled.Visibility
                                    },
                                    contentDescription = if (passwordVisible) {
                                        "إخفاء كلمة المرور"
                                    } else {
                                        "إظهار كلمة المرور"
                                    },
                                    tint = if (busy) Muted.copy(alpha = 0.5f) else Muted,
                                )
                            }
                        },
                        colors = loginFieldColors(),
                        shape = FieldShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(passwordFocus),
                    )

                    if (error != null) {
                        Spacer(Modifier.height(12.dp))
                        AuthMessage(error, AuthMessageTone.Error)
                    }

                    Spacer(Modifier.height(16.dp))
                    AuthPrimaryButton(
                        text = "دخول",
                        loadingText = "جارٍ الدخول…",
                        loading = busy,
                        onClick = submitSignIn,
                    )
                } else {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            nameError = null
                        },
                        label = { Text("الاسم الكامل") },
                        singleLine = true,
                        enabled = !joinBusy,
                        isError = nameError != null,
                        supportingText = {
                            Text(
                                text = nameError ?: "الاسم كما تحبين أن يظهر.",
                                color = if (nameError != null) BrandError else Muted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { joinEmailFocus.requestFocus() }),
                        colors = loginFieldColors(),
                        shape = FieldShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(nameFocus),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = joinEmail,
                        onValueChange = {
                            joinEmail = it.trim()
                            joinEmailError = null
                        },
                        label = { Text("البريد الإلكتروني") },
                        singleLine = true,
                        enabled = !joinBusy,
                        isError = joinEmailError != null,
                        supportingText = {
                            Text(
                                text = joinEmailError ?: "بريد للتواصل بعد قبول الطلب.",
                                color = if (joinEmailError != null) BrandError else Muted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(onNext = { phoneFocus.requestFocus() }),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            textDirection = TextDirection.Ltr,
                            textAlign = TextAlign.Start,
                        ),
                        colors = loginFieldColors(),
                        shape = FieldShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(joinEmailFocus),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = phone,
                        onValueChange = {
                            phone = it
                            phoneError = null
                        },
                        label = { Text("رقم الهاتف") },
                        singleLine = true,
                        enabled = !joinBusy,
                        isError = phoneError != null,
                        supportingText = {
                            Text(
                                text = phoneError ?: "رقم للتواصل عند المراجعة.",
                                color = if (phoneError != null) BrandError else Muted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(onNext = { cityFocus.requestFocus() }),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            textDirection = TextDirection.Ltr,
                            textAlign = TextAlign.Start,
                        ),
                        colors = loginFieldColors(),
                        shape = FieldShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(phoneFocus),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = city,
                        onValueChange = {
                            city = it
                            cityError = null
                        },
                        label = { Text("المدينة") },
                        singleLine = true,
                        enabled = !joinBusy,
                        isError = cityError != null,
                        supportingText = {
                            Text(
                                text = cityError ?: "مدينتك الحالية.",
                                color = if (cityError != null) BrandError else Muted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { noteFocus.requestFocus() }),
                        colors = loginFieldColors(),
                        shape = FieldShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(cityFocus),
                    )
                    Spacer(Modifier.height(12.dp))

                    Text(
                        "المستوى المرغوب",
                        color = DeepGreen,
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LevelOptions.forEach { (value, label) ->
                            LevelOption(
                                text = label,
                                selected = level == value,
                                enabled = !joinBusy,
                                modifier = Modifier.weight(1f),
                                onClick = { level = value },
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "اختاري المستوى الذي ترغبين بالانضمام إليه.",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("ملاحظة (اختياري)") },
                        enabled = !joinBusy,
                        minLines = 2,
                        maxLines = 4,
                        supportingText = {
                            Text(
                                text = "اختياري: اذكري ما حفظتِه أو أي ملاحظة.",
                                color = Muted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submitJoin() }),
                        colors = loginFieldColors(),
                        shape = FieldShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(noteFocus),
                    )

                    val status = joinStatus
                    if (status != null) {
                        Spacer(Modifier.height(12.dp))
                        val tone = when {
                            status.startsWith("تعذّر") -> AuthMessageTone.Error
                            status.startsWith("تم") -> AuthMessageTone.Success
                            else -> AuthMessageTone.Info
                        }
                        AuthMessage(status, tone)
                    }

                    Spacer(Modifier.height(16.dp))
                    AuthPrimaryButton(
                        text = "إرسال طلب الالتحاق",
                        loadingText = "جارٍ إرسال الطلب…",
                        loading = joinBusy,
                        onClick = submitJoin,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = if (tab == 0) {
                    "تُحدّد الصلاحيات حسب حسابك المعتمد."
                } else {
                    "ستصلك دعوة بعد قبول الطلب."
                },
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AuthTabs(
    selectedTab: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Cream)
            .border(1.dp, CardLine, shape)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AuthTab(
            text = "تسجيل الدخول",
            selected = selectedTab == 0,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(0) },
        )
        AuthTab(
            text = "طلب التحاق",
            selected = selectedTab == 1,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(1) },
        )
    }
}

@Composable
private fun AuthTab(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (selected) Gold else Color.Transparent)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = when {
                !enabled -> Muted.copy(alpha = 0.6f)
                selected -> DeepGreen
                else -> Ink
            },
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LevelOption(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (selected) GoldLight else Paper)
            .border(1.dp, if (selected) GoldDark else CardLine, shape)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 4.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = when {
                !enabled -> Muted.copy(alpha = 0.6f)
                selected -> DeepGreen
                else -> Ink
            },
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AuthPrimaryButton(
    text: String,
    loadingText: String,
    loading: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = !loading,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        shape = RoundedCornerShape(26.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Gold,
            contentColor = DeepGreen,
            disabledContainerColor = Gold.copy(alpha = 0.55f),
            disabledContentColor = DeepGreen,
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(18.dp)
                    .semantics { contentDescription = loadingText },
                color = DeepGreen,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(8.dp))
            Text(loadingText, style = MaterialTheme.typography.labelLarge)
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun AuthMessage(text: String, tone: AuthMessageTone) {
    val shape = RoundedCornerShape(12.dp)
    val (container, borderColor, contentColor) = when (tone) {
        AuthMessageTone.Error -> Triple(BrandErrorSurface, BrandError.copy(alpha = 0.35f), BrandError)
        AuthMessageTone.Success -> Triple(BrandSuccessSurface, BrandSuccess.copy(alpha = 0.35f), BrandSuccess)
        AuthMessageTone.Info -> Triple(Cream, CardLine, Ink)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .border(1.dp, borderColor, shape)
            .padding(12.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun BootScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(MaqraatalNoorBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.logo_maqraatalnoor),
                contentDescription = null,
                modifier = Modifier.size(190.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "مقرأة حكمة النور",
                color = DeepGreen,
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                "لتعليم القرآن الكريم",
                color = DeepGreen,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(DeepGreen)
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Text(
                    "مقرأة نسائية متخصصة",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator(color = DeepGreen, modifier = Modifier.size(28.dp))
        }
    }
}

/** شاشة من دخلت بلا عضوية بعد: تُدخل كود الدعوة. */
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
            modifier = Modifier.size(130.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text("أهلا $name", color = Color.White, style = MaterialTheme.typography.titleLarge)
        Text(
            "حسابك جاهز لكنه ليس في المقرأة بعد.\nأدخلي كود الدعوة الذي وصلك من المشرفة.",
            color = Cream,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.trim().uppercase() },
            label = { Text("كود الدعوة") },
            singleLine = true,
            enabled = !busy,
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(error, color = Color(0xFFF3B0AA), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(16.dp))
        if (busy) CircularProgressIndicator(color = Gold, modifier = Modifier.size(32.dp))
        else {
            GoldButton("الانضمام", { onRedeem(code) }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onSignOut) { Text("تسجيل الخروج", color = Cream) }
        }
    }
}

/** شاشة من سجّلت الدخول وعضويتها لم تُفعّل بعد أو محظورة. */
@Composable
fun PendingApprovalScreen(
    name: String,
    banned: Boolean,
    busy: Boolean,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepGreen)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 28.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.logo_maqraatalnoor),
            contentDescription = null,
            modifier = Modifier.size(130.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text("أهلا $name", color = Color.White, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            if (banned) "حسابك موقوف. للاستفسار تواصلي مع المشرفة العامة."
            else "طلبك وصل إلى المشرفة العامة، وسيُفتح حسابك بعد القبول.",
            color = Cream,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))
        if (busy) CircularProgressIndicator(color = Gold, modifier = Modifier.size(30.dp))
        else {
            GoldButton("تحديث الحالة", onRefresh, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onSignOut) { Text("تسجيل الخروج", color = Cream) }
        }
    }
}

package org.maqraatalnoor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Verified
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.maqraatalnoor.domain.SupervisorAccountDraft
import org.maqraatalnoor.domain.SupervisorAccountErrors
import org.maqraatalnoor.ui.components.InitialAvatar
import org.maqraatalnoor.ui.components.MaqraatalNoorCard
import org.maqraatalnoor.ui.components.MaqraatalNoorTopBar
import org.maqraatalnoor.ui.components.SectionTitle
import org.maqraatalnoor.ui.theme.CardLine
import org.maqraatalnoor.ui.theme.DeepGreen
import org.maqraatalnoor.ui.theme.DeepGreenDark
import org.maqraatalnoor.ui.theme.Gold
import org.maqraatalnoor.ui.theme.GoldLight
import org.maqraatalnoor.ui.theme.Ink
import org.maqraatalnoor.ui.theme.Muted
import org.maqraatalnoor.ui.theme.Paper
import org.maqraatalnoor.ui.theme.Sage
import org.maqraatalnoor.ui.viewmodels.OwnerDashboardViewModel
import org.maqraatalnoor.ui.viewmodels.SupervisorCreationState

private val BrandError = Color(0xFFA92F2F)
private val BrandErrorSurface = Color(0xFFFBEDED)
private val BrandSuccess = Color(0xFF2F6B4F)
private val BrandSuccessSurface = Color(0xFFEDF5EF)
private val CardShape = RoundedCornerShape(20.dp)
private val FieldShape = RoundedCornerShape(14.dp)

private data class SupervisorPermission(val title: String, val detail: String)

private val supervisorPermissions = listOf(
    SupervisorPermission("طلبات الالتحاق", "مراجعة الطلبات وقبول الطالبات أو رفضهن"),
    SupervisorPermission("الدعوات", "إنشاء دعوات الطالبات والمعلمات"),
    SupervisorPermission("المجموعات", "إنشاء المجموعات وإسناد الطالبات والمستويات"),
    SupervisorPermission("الجلسات والمتابعة", "إدارة الجلسات والحضور والتقييمات"),
    SupervisorPermission("العضوات", "متابعة العضوية والإشراف اليومي"),
)

/**
 * Owner-only dashboard. It is registered in the navigation graph exclusively
 * when the authenticated `public.is_owner()` result is true; the server also
 * re-checks the caller and enforces service-role-only provisioning.
 */
@Composable
fun OwnerDashboardScreen(
    ownerName: String,
    ownerEmail: String,
    ownerStatus: String,
    onBack: () -> Unit,
    viewModel: OwnerDashboardViewModel = viewModel(),
) {
    val creation by viewModel.state.collectAsState()
    var draft by remember { mutableStateOf(SupervisorAccountDraft()) }
    var errors by remember { mutableStateOf(SupervisorAccountErrors()) }
    var passwordVisible by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val nameFocus = remember { FocusRequester() }
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }

    val busy = creation is SupervisorCreationState.Submitting

    LaunchedEffect(creation) {
        if (creation is SupervisorCreationState.Success) {
            // The password leaves composition memory as soon as the server confirms.
            draft = draft.afterSuccess()
            errors = SupervisorAccountErrors()
            passwordVisible = false
        }
    }

    val submit: () -> Unit = {
        if (!busy) {
            val result = draft.validate()
            errors = result
            if (!result.isValid) {
                when {
                    result.fullName != null -> nameFocus.requestFocus()
                    result.email != null -> emailFocus.requestFocus()
                    else -> passwordFocus.requestFocus()
                }
            } else {
                focusManager.clearFocus(force = true)
                viewModel.createSupervisor(draft.fullName, draft.email, draft.password)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        MaqraatalNoorTopBar(
            title = "لوحة المالكة",
            subtitle = "إدارة المقرأة وحساب المشرفة العامة",
            onBack = onBack,
        )

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            OwnerIdentityCard(
                name = ownerName,
                email = ownerEmail,
                status = ownerStatus,
            )

            Spacer(Modifier.height(20.dp))
            SectionTitle("إنشاء حساب المشرفة العامة")
            Spacer(Modifier.height(8.dp))
            MaqraatalNoorCard(modifier = Modifier.border(1.dp, CardLine, CardShape)) {
                Text(
                    "يُنشأ الحساب مباشرة بدور المشرفة العامة، مع عضوية نشطة في الحلقة العامة. لا تُرسل دعوة بريدية.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = draft.fullName,
                    onValueChange = {
                        draft = draft.copy(fullName = it)
                        errors = errors.copy(fullName = null)
                        viewModel.clearMessage()
                    },
                    label = { Text("الاسم الكامل") },
                    singleLine = true,
                    enabled = !busy,
                    isError = errors.fullName != null,
                    supportingText = {
                        Text(
                            text = errors.fullName ?: "الاسم كما سيظهر في ملف المشرفة.",
                            color = if (errors.fullName != null) BrandError else Muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { emailFocus.requestFocus() }),
                    colors = ownerFieldColors(),
                    shape = FieldShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(nameFocus),
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = draft.email,
                    onValueChange = {
                        draft = draft.copy(email = it)
                        errors = errors.copy(email = null)
                        viewModel.clearMessage()
                    },
                    label = { Text("البريد الإلكتروني") },
                    singleLine = true,
                    enabled = !busy,
                    isError = errors.email != null,
                    supportingText = {
                        Text(
                            text = errors.email ?: "ستسجّل الدخول بهذا البريد.",
                            color = if (errors.email != null) BrandError else Muted,
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
                    colors = ownerFieldColors(),
                    shape = FieldShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(emailFocus),
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = draft.password,
                    onValueChange = {
                        draft = draft.copy(password = it)
                        errors = errors.copy(password = null)
                        viewModel.clearMessage()
                    },
                    label = { Text("كلمة المرور الأولية") },
                    singleLine = true,
                    enabled = !busy,
                    isError = errors.password != null,
                    supportingText = {
                        Text(
                            text = errors.password
                                ?: "8 أحرف على الأقل. لا تُحفظ في التطبيق ولا تُسجَّل.",
                            color = if (errors.password != null) BrandError else Muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
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
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    colors = ownerFieldColors(),
                    shape = FieldShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(passwordFocus),
                )

                when (val state = creation) {
                    is SupervisorCreationState.Success -> {
                        Spacer(Modifier.height(12.dp))
                        OwnerMessage(
                            text = "أُنشئ حساب المشرفة العامة «${state.fullName}» بنجاح. " +
                                "يمكنها تسجيل الدخول بالبريد وكلمة المرور الأولية.",
                            success = true,
                        )
                    }

                    is SupervisorCreationState.Failed -> {
                        Spacer(Modifier.height(12.dp))
                        OwnerMessage(text = state.message, success = false)
                    }

                    else -> Unit
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = submit,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Gold,
                        contentColor = DeepGreen,
                        disabledContainerColor = Gold.copy(alpha = 0.55f),
                        disabledContentColor = DeepGreen,
                    ),
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = DeepGreen,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("جارٍ إنشاء الحساب…", style = MaterialTheme.typography.labelLarge)
                    } else {
                        Text("إنشاء الحساب الآن", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "لا تُعاد كلمة المرور من الخادم. تُمسح من الحقل بعد نجاح الإنشاء.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }

            Spacer(Modifier.height(22.dp))
            SectionTitle("صلاحيات المشرفة العامة")
            Spacer(Modifier.height(8.dp))
            PermissionsCard()

            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun OwnerIdentityCard(name: String, email: String, status: String) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(DeepGreen, DeepGreenDark)))
            .border(1.dp, Gold.copy(alpha = 0.55f), shape)
            .padding(18.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InitialAvatar(
                    initial = name.trim().takeLast(1).ifBlank { "ع" },
                    size = 60,
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
                    Spacer(Modifier.height(3.dp))
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
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OwnerPill("مالكة المقرأة", verified = true)
                OwnerPill("الحساب $status", verified = false)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "هذا الحساب محمي؛ لا يمكن لبقية الحسابات تعديله أو إزالة عضويته.",
                style = MaterialTheme.typography.bodySmall,
                color = GoldLight.copy(alpha = 0.9f),
            )
        }
    }
}

@Composable
private fun OwnerPill(text: String, verified: Boolean) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (verified) Gold else BrandSuccessSurface)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (verified) Icons.Filled.Verified else Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = if (verified) DeepGreen else BrandSuccess,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text,
            color = if (verified) DeepGreen else BrandSuccess,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun OwnerMessage(text: String, success: Boolean) {
    val container = if (success) BrandSuccessSurface else BrandErrorSurface
    val border = if (success) BrandSuccess.copy(alpha = 0.35f) else BrandError.copy(alpha = 0.35f)
    val content = if (success) BrandSuccess else BrandError
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .border(1.dp, border, shape)
            .padding(12.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = if (success) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text,
                color = content,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PermissionsCard() {
    MaqraatalNoorCard(modifier = Modifier.border(1.dp, CardLine, CardShape)) {
        Text(
            "ملخص ثابت للصلاحيات التي يفرضها الخادم على دور المشرفة العامة.",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
        )
        Spacer(Modifier.height(10.dp))
        supervisorPermissions.forEachIndexed { index, permission ->
            if (index > 0) {
                Spacer(Modifier.height(10.dp))
            }
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Sage),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = DeepGreen,
                        modifier = Modifier.size(15.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        permission.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        permission.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Sage)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = DeepGreen,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "لا تشمل الصلاحيات تغيير دور المالكة أو إنشاء مالكة أخرى.",
                style = MaterialTheme.typography.bodySmall,
                color = DeepGreen,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ownerFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Ink,
    unfocusedTextColor = Ink,
    disabledTextColor = Ink.copy(alpha = 0.75f),
    errorTextColor = Ink,
    focusedContainerColor = Paper,
    unfocusedContainerColor = Paper,
    disabledContainerColor = Paper,
    errorContainerColor = Paper,
    focusedBorderColor = Gold,
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

package org.maqraatalnoor.domain

/**
 * Owner routes are gated by the authenticated `public.is_owner()` result, never
 * by the ordinary profile role. The owner's profile role is also `supervisor`,
 * so a role string alone would be both insufficient and ambiguous.
 */
object OwnerAccess {
    /**
     * [profileRole] is accepted so call sites cannot pretend the role is the
     * gate; it is deliberately ignored. Only the server `is_owner()` result
     * opens owner routes.
     */
    @Suppress("UNUSED_PARAMETER")
    fun canOpenDashboard(isOwnerFromServer: Boolean, profileRole: String?): Boolean =
        isOwnerFromServer
}

/** Pure validation and user-facing error mapping for the supervisor form. */
object SupervisorAccountPolicy {

    const val NAME_MIN = 2
    const val NAME_MAX = 80
    const val EMAIL_MAX = 254
    const val PASSWORD_MIN = 8
    const val PASSWORD_MAX = 72

    internal val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    private val controlCharacters = Regex("\\p{Cntrl}")
    private val whitespace = Regex("\\s+")

    /** Trim, remove control characters and collapse whitespace before validating. */
    fun sanitizeName(raw: String): String =
        raw.replace(controlCharacters, " ")
            .replace(whitespace, " ")
            .trim()

    fun normalizeEmail(raw: String): String = raw.trim().lowercase()

    /**
     * Maps a failed Edge Function call to a safe Arabic message. It never
     * echoes a server payload, URL or credential.
     */
    fun friendlyCreationError(status: Int?, message: String): String {
        val raw = message.lowercase()
        return when {
            raw.contains("active_supervisor_exists") ->
                "يوجد حساب مشرفة عامة نشط بالفعل. لا يمكن إنشاء حساب ثانٍ."

            raw.contains("email_exists") ->
                "يوجد حساب مسجّل بهذا البريد الإلكتروني. استخدمي بريدًا آخر."

            raw.contains("weak_password") ->
                "كلمة المرور الأولية لا تحقق سياسة الأمان. اختاري كلمة أقوى."

            raw.contains("owner_target_forbidden") ->
                "لا يمكن استخدام حساب المالكة لإنشاء حساب مشرفة عامة."

            raw.contains("profile_missing") ->
                "تعذّر العثور على الملف الشخصي للحساب الجديد."

            raw.contains("main_halqa_missing") ->
                "الحلقة العامة غير مهيأة على الخادم. تواصلي مع الدعم."

            raw.contains("invalid_payload") ->
                "تحققي من الاسم والبريد وكلمة المرور. كلمة المرور بين 8 و72 حرفًا."

            status == 401 || raw.contains("unauthorized") ->
                "انتهت الجلسة. سجّلي الدخول من جديد ثم أعيدي المحاولة."

            status == 403 || raw.contains("owner_only") ->
                "هذا الإجراء متاح للمالكة فقط."

            raw.contains("unable to resolve host") || raw.contains("timeout") ->
                "تعذّر الاتصال بالشبكة. تحققي من الاتصال ثم حاولي مجددًا."

            raw.contains("cleanup_failed") ->
                "تعذّر إكمال إنشاء الحساب. تواصلي مع الدعم قبل إعادة المحاولة."

            raw.contains("authorization_check_failed") ->
                "تعذّر التحقق من صلاحية المالكة. أعيدي المحاولة بعد قليل."

            raw.contains("server_not_configured") ->
                "الخادم غير مهيأ لإنشاء الحسابات حاليًا."

            else ->
                "تعذّر إنشاء الحساب. تحققي من البيانات وحاولي مجددًا."
        }
    }
}

/** Local-only form values. The password is never stored in a ViewModel or on disk. */
data class SupervisorAccountDraft(
    val fullName: String = "",
    val email: String = "",
    val password: String = "",
) {
    fun validate(): SupervisorAccountErrors {
        val name = SupervisorAccountPolicy.sanitizeName(fullName)
        val mail = SupervisorAccountPolicy.normalizeEmail(email)

        val nameError = when {
            name.isEmpty() -> "أدخلي الاسم الكامل."
            name.length < SupervisorAccountPolicy.NAME_MIN ||
                name.length > SupervisorAccountPolicy.NAME_MAX ->
                "الاسم بين حرفين و${SupervisorAccountPolicy.NAME_MAX} حرفًا."

            else -> null
        }

        val emailError = when {
            mail.isEmpty() -> "أدخلي البريد الإلكتروني."
            mail.length > SupervisorAccountPolicy.EMAIL_MAX -> "البريد الإلكتروني طويل جدًا."
            !SupervisorAccountPolicy.emailPattern.matches(mail) ->
                "أدخلي بريدًا إلكترونيًا صحيحًا."

            else -> null
        }

        val passwordError = when {
            password.isEmpty() -> "أدخلي كلمة مرور أولية."
            password.isBlank() -> "كلمة المرور لا يمكن أن تكون مسافات فقط."
            password.length < SupervisorAccountPolicy.PASSWORD_MIN ->
                "كلمة المرور ${SupervisorAccountPolicy.PASSWORD_MIN} أحرف على الأقل."

            password.length > SupervisorAccountPolicy.PASSWORD_MAX ->
                "كلمة المرور أطول من الحد المسموح."

            else -> null
        }

        return SupervisorAccountErrors(nameError, emailError, passwordError)
    }

    /** Success clears the secret locally; name and email stay for confirmation. */
    fun afterSuccess(): SupervisorAccountDraft = copy(password = "")
}

data class SupervisorAccountErrors(
    val fullName: String? = null,
    val email: String? = null,
    val password: String? = null,
) {
    val isValid: Boolean
        get() = fullName == null && email == null && password == null
}

package org.maqraatalnoor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maqraatalnoor.data.remote.RemoteProfile
import org.maqraatalnoor.data.remote.SupabaseApi
import org.maqraatalnoor.domain.OwnerAccess
import org.maqraatalnoor.domain.SupervisorAccountDraft
import org.maqraatalnoor.domain.SupervisorAccountPolicy
import org.maqraatalnoor.ui.viewmodels.AuthState

class SupervisorAccountTest {

    private fun validDraft() = SupervisorAccountDraft(
        fullName = "أم محمد",
        email = "supervisor@test.local",
        password = "Noor-1234",
    )

    @Test
    fun validDraft_passesValidation() {
        assertTrue(validDraft().validate().isValid)
    }

    @Test
    fun name_isSanitizedAndBounded() {
        assertEquals(
            "أم محمد",
            SupervisorAccountPolicy.sanitizeName("  أم\u0000   محمد  "),
        )
        assertFalse(
            SupervisorAccountDraft(fullName = " ", email = "a@b.co", password = "12345678")
                .validate().isValid,
        )
        assertTrue(
            SupervisorAccountDraft(
                fullName = "ا".repeat(SupervisorAccountPolicy.NAME_MAX + 1),
                email = "a@b.co",
                password = "12345678",
            ).validate().fullName != null,
        )
    }

    @Test
    fun email_isNormalizedAndValidated() {
        assertEquals("sara@test.local", SupervisorAccountPolicy.normalizeEmail("  SARA@Test.Local "))
        assertTrue(
            SupervisorAccountDraft(fullName = "أم سارة", email = "not-an-email", password = "12345678")
                .validate().email != null,
        )
        assertTrue(
            SupervisorAccountDraft(fullName = "أم سارة", email = "sara@test.local", password = "12345678")
                .validate().isValid,
        )
    }

    @Test
    fun password_isNeverTrimmedAndHasBounds() {
        assertTrue(
            SupervisorAccountDraft(fullName = "أم سارة", email = "sara@test.local", password = "1234567")
                .validate().password != null,
        )
        assertTrue(
            SupervisorAccountDraft(
                fullName = "أم سارة",
                email = "sara@test.local",
                password = "ا".repeat(SupervisorAccountPolicy.PASSWORD_MAX + 1),
            ).validate().password != null,
        )
        assertTrue(
            SupervisorAccountDraft(fullName = "أم سارة", email = "sara@test.local", password = "        ")
                .validate().password != null,
        )
        // Spaces are significant: the raw value is kept, only the blank check rejects it.
        val spaced = SupervisorAccountDraft(
            fullName = "أم سارة",
            email = "sara@test.local",
            password = "  Noor-1234  ",
        )
        assertNull(spaced.validate().password)
        assertEquals("  Noor-1234  ", spaced.password)
    }

    @Test
    fun success_clearsPasswordOnly() {
        val cleared = validDraft().afterSuccess()
        assertEquals("", cleared.password)
        assertEquals("أم محمد", cleared.fullName)
        assertEquals("supervisor@test.local", cleared.email)
    }

    @Test
    fun ownerDashboard_requiresServerOwnerResult_notSupervisorRole() {
        assertFalse(OwnerAccess.canOpenDashboard(isOwnerFromServer = false, profileRole = "supervisor"))
        assertFalse(OwnerAccess.canOpenDashboard(isOwnerFromServer = false, profileRole = null))
        assertTrue(OwnerAccess.canOpenDashboard(isOwnerFromServer = true, profileRole = "supervisor"))
    }

    @Test
    fun signedInSupervisor_isNotOwnerWithoutServerFlag() {
        val state = AuthState.SignedIn(
            session = SupabaseApi.AuthSession(
                accessToken = "user-access-jwt",
                refreshToken = "refresh",
                userId = "user-id",
                email = "supervisor@test.local",
            ),
            profile = RemoteProfile(id = "user-id", name = "مشرفة", role = "supervisor"),
            membership = null,
            halqa = null,
            members = emptyList(),
        )

        // The ordinary profile role makes her staff, never the owner.
        assertTrue(state.isStaff)
        assertFalse(state.isOwner)
    }

    @Test
    fun creationErrors_areSafeAndSpecific() {
        assertEquals(
            "يوجد حساب مشرفة عامة نشط بالفعل. لا يمكن إنشاء حساب ثانٍ.",
            SupervisorAccountPolicy.friendlyCreationError(409, "active_supervisor_exists"),
        )
        assertEquals(
            "يوجد حساب مسجّل بهذا البريد الإلكتروني. استخدمي بريدًا آخر.",
            SupervisorAccountPolicy.friendlyCreationError(409, "email_exists"),
        )
        assertEquals(
            "هذا الإجراء متاح للمالكة فقط.",
            SupervisorAccountPolicy.friendlyCreationError(403, "owner_only"),
        )
        assertEquals(
            "انتهت الجلسة. سجّلي الدخول من جديد ثم أعيدي المحاولة.",
            SupervisorAccountPolicy.friendlyCreationError(401, "unauthorized"),
        )
        assertEquals(
            "تعذّر إنشاء الحساب. تحققي من البيانات وحاولي مجددًا.",
            SupervisorAccountPolicy.friendlyCreationError(500, "provision_failed"),
        )
    }
}

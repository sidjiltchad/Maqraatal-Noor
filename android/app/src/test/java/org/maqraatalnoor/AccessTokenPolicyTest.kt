package org.maqraatalnoor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.maqraatalnoor.data.remote.AccessTokenPolicy
import org.maqraatalnoor.data.remote.MissingAuthSessionException

class AccessTokenPolicyTest {

    private val anonKey = "legacy-anon-key"

    @Test
    fun userToken_accepted() {
        assertEquals("user-access-jwt", AccessTokenPolicy.requireUserToken("user-access-jwt", anonKey))
    }

    @Test
    fun blankOrAbsentToken_rejected() {
        for (token in listOf(null, "", "   ")) {
            assertRejected(token)
        }
    }

    @Test
    fun anonKey_rejected() {
        assertRejected(anonKey)
        assertRejected("  $anonKey  ")
    }

    @Test
    fun isUserToken_matchesRequirePolicy() {
        assertFalse(AccessTokenPolicy.isUserToken(null, anonKey))
        assertFalse(AccessTokenPolicy.isUserToken("", anonKey))
        assertFalse(AccessTokenPolicy.isUserToken(anonKey, anonKey))
        assertTrue(AccessTokenPolicy.isUserToken("user-access-jwt", anonKey))
    }

    private fun assertRejected(token: String?) {
        try {
            AccessTokenPolicy.requireUserToken(token, anonKey)
            fail("expected a local rejection for token '$token'")
        } catch (_: MissingAuthSessionException) {
            // Expected: no protected request may be issued.
        }
    }
}

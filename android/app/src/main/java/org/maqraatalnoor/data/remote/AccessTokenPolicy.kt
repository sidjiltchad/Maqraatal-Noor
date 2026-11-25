package org.maqraatalnoor.data.remote

/**
 * Guards PostgREST/RPC calls that must run as a signed-in user.
 *
 * The member directory is intentionally not readable by `anon`; sending a
 * missing, blank, or public anon token would only produce a misleading
 * PostgREST 401. Authenticated requests therefore fail closed locally before
 * any HTTP request is built. The anon key remains available solely to the
 * explicitly anonymous join-request RPC and the realtime `apikey` parameter.
 */
object AccessTokenPolicy {

    /** True when [token] is a non-blank token that is not the public anon key. */
    fun isUserToken(token: String?, anonKey: String): Boolean {
        val value = token.orEmpty().trim()
        return value.isNotEmpty() && value != anonKey.trim()
    }

    /** Returns the trimmed user token, or throws if no authenticated session exists. */
    fun requireUserToken(token: String?, anonKey: String): String {
        val value = token.orEmpty().trim()
        if (!isUserToken(value, anonKey)) throw MissingAuthSessionException()
        return value
    }
}

/** Raised locally instead of issuing an unauthenticated protected request. */
class MissingAuthSessionException : Exception("انتهت الجلسة، سجّلي الدخول من جديد")

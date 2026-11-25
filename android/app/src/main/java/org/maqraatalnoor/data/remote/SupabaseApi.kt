package org.maqraatalnoor.data.remote

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.maqraatalnoor.BuildConfig
import java.util.concurrent.TimeUnit

/**
 * عميل Supabase بسيط: مصادقة + PostgREST + RPC، عبر OkHttp فقط.
 * لا مكتبات ثقيلة، وكل الاستدعاءات تُنفَّذ من coroutine على Dispatchers.IO.
 */
object SupabaseApi {

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val baseUrl: String get() = BuildConfig.SUPABASE_URL.trimEnd('/')

    /** wss://<ref>.supabase.co/realtime/v1/websocket */
    val realtimeUrl: String get() = baseUrl.replace("https://", "wss://") + "/realtime/v1/websocket"

    val anonKeyPublic: String get() = BuildConfig.SUPABASE_ANON_KEY
    private val anonKey: String get() = BuildConfig.SUPABASE_ANON_KEY

    data class AuthSession(
        val accessToken: String,
        val refreshToken: String,
        val userId: String,
        val email: String,
    )

    class ApiException(val status: Int, message: String) : Exception(message)

    private fun execute(request: Request): String {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    val obj = JSONObject(body)
                    obj.optString("message")
                        .ifBlank { obj.optString("error_description") }
                        .ifBlank { obj.optString("error") }
                }.getOrNull().orEmpty().ifBlank { body }.ifBlank { "خطأ غير معروف" }
                throw ApiException(response.code, message)
            }
            return body
        }
    }

    private fun parseSession(obj: JSONObject): AuthSession {
        val user = obj.optJSONObject("user")
        return AuthSession(
            accessToken = obj.getString("access_token"),
            refreshToken = obj.optString("refresh_token"),
            userId = user?.optString("id").orEmpty(),
            email = user?.optString("email").orEmpty(),
        )
    }

    // ---------------------------------------------------------------- auth ----
    fun signIn(email: String, password: String): AuthSession {
        val payload = JSONObject().put("email", email.trim()).put("password", password)
        val request = Request.Builder()
            .url("$baseUrl/auth/v1/token?grant_type=password")
            .addHeader("apikey", anonKey)
            .post(payload.toString().toRequestBody(jsonMedia))
            .build()
        return parseSession(JSONObject(execute(request)))
    }

    fun refresh(refreshToken: String): AuthSession {
        val payload = JSONObject().put("refresh_token", refreshToken)
        val request = Request.Builder()
            .url("$baseUrl/auth/v1/token?grant_type=refresh_token")
            .addHeader("apikey", anonKey)
            .post(payload.toString().toRequestBody(jsonMedia))
            .build()
        return parseSession(JSONObject(execute(request)))
    }

    fun signOut(accessToken: String) {
        val request = Request.Builder()
            .url("$baseUrl/auth/v1/logout")
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $accessToken")
            .post("{}".toRequestBody(jsonMedia))
            .build()
        runCatching { execute(request) }
    }

    // ----------------------------------------------------------- rest/rpc ----

    /**
     * لا تُبنى أي طلبية محمية بتوكن فارغ أو بمفتاح anon: الحماية في قاعدة
     * البيانات تتطلب جلسة مستخدمة حقيقية، والسماح بغيرها يعرض 401 مضللا.
     */
    private fun userBearer(token: String): String =
        AccessTokenPolicy.requireUserToken(token, anonKey)

    fun get(path: String, token: String): JSONArray {
        val bearer = userBearer(token)
        val request = Request.Builder()
            .url("$baseUrl/rest/v1/$path")
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $bearer")
            .get()
            .build()
        return JSONArray(execute(request))
    }

    /** إدخال صف في جدول وإرجاع الصفوف المُنشأة. */
    fun post(table: String, payload: JSONObject, token: String): JSONArray {
        val bearer = userBearer(token)
        val request = Request.Builder()
            .url("$baseUrl/rest/v1/$table")
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $bearer")
            .addHeader("Prefer", "return=representation")
            .post(payload.toString().toRequestBody(jsonMedia))
            .build()
        return JSONArray(execute(request))
    }

    /** تحديث صفوف جدول (PATCH) وإرجاع الصفوف المحدَّثة. */
    fun patch(table: String, payload: JSONObject, token: String): String {
        val bearer = userBearer(token)
        val request = Request.Builder()
            .url("$baseUrl/rest/v1/$table")
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $bearer")
            .addHeader("Prefer", "return=representation")
            .patch(payload.toString().toRequestBody(jsonMedia))
            .build()
        return execute(request)
    }

    /** دالة RPC بصلاحية anon (قبل تسجيل الدخول) — مثل إرسال طلب التحاق. */
    fun rpcAnon(name: String, payload: JSONObject): String {
        val request = Request.Builder()
            .url("$baseUrl/rest/v1/rpc/$name")
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $anonKey")
            .post(payload.toString().toRequestBody(jsonMedia))
            .build()
        return execute(request)
    }

    /** ينفّذ دالة RPC ويعيد نص الاستجابة كما هو. */
    fun rpc(name: String, payload: JSONObject, token: String): String {
        val bearer = userBearer(token)
        val request = Request.Builder()
            .url("$baseUrl/rest/v1/rpc/$name")
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $bearer")
            .post(payload.toString().toRequestBody(jsonMedia))
            .build()
        return execute(request)
    }

    /**
     * Invokes an authenticated Edge Function with the caller's own JWT.
     * The service-role key is never part of the Android build or this request.
     */
    fun invokeFunction(name: String, payload: JSONObject, token: String): JSONObject {
        val bearer = userBearer(token)
        val request = Request.Builder()
            .url("$baseUrl/functions/v1/$name")
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $bearer")
            .post(payload.toString().toRequestBody(jsonMedia))
            .build()
        return JSONObject(execute(request))
    }
}

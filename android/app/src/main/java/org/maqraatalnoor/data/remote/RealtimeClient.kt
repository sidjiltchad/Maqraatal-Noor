package org.maqraatalnoor.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * عميل Supabase Realtime (بروتوكول Phoenix) فوق WebSocket.
 * يشترك في تغييرات جداول الحلقة، ويستدعي [onChange] عند كل تغيير
 * فيُحدَّث ما على الشاشة فورا بدل الاستقصاء الدوري.
 */
class RealtimeClient(
    private val accessToken: String,
    private val tables: List<String>,
    private val onChange: () -> Unit,
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var socket: WebSocket? = null
    private val ref = AtomicInteger(1)

    @Volatile
    var connected: Boolean = false
        private set

    @Volatile
    var lastEventAt: Long = 0L
        private set

    fun connect() {
        val url = "${SupabaseApi.realtimeUrl}?apikey=${SupabaseApi.anonKeyPublic}" +
            "&access_token=$accessToken&vsn=1.0.0"
        val request = Request.Builder().url(url).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connected = true
                tables.forEachIndexed { index, table -> webSocket.join(table, ref.getAndIncrement()) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val message = JSONObject(text)
                    when (message.optString("event")) {
                        "postgres_changes" -> {
                            lastEventAt = System.currentTimeMillis()
                            onChange()
                        }

                        "phx_reply" -> {
                            val status = message.optJSONObject("payload")?.optString("status")
                            if (status == "error") {
                                // إعادة المحاولة مرة واحدة على الاشتراك الفاشل
                                onChange()
                            }
                        }

                        "phx_error" -> connected = false
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                connected = false
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connected = false
            }
        })
    }

    fun close() {
        runCatching {
            tables.forEach { table ->
                socket?.send(
                    JSONObject()
                        .put("topic", "realtime:public:$table")
                        .put("event", "phx_leave")
                        .put("payload", JSONObject())
                        .put("ref", ref.getAndIncrement().toString())
                        .toString(),
                )
            }
            socket?.close(1000, null)
        }
        socket = null
        connected = false
    }

    private fun WebSocket.join(table: String, refId: Int) {
        val payload = JSONObject().put(
            "config",
            JSONObject().put(
                "postgres_changes",
                JSONArray().put(
                    JSONObject()
                        .put("event", "*")
                        .put("schema", "public")
                        .put("table", table),
                ),
            ),
        )
        send(
            JSONObject()
                .put("topic", "realtime:public:$table")
                .put("event", "phx_join")
                .put("payload", payload)
                .put("ref", refId.toString())
                .toString(),
        )
    }
}

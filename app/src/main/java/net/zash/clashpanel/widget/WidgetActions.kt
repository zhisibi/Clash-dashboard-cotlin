package net.zash.clashpanel.widget

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Direct backend calls for the interactive widget (1.4.0).
 *
 * The widget's BroadcastReceiver cannot reach MainViewModel/CoreApi, so it reads the active backend
 * from the app's own SharedPreferences ("clash_panel": `backends` JSON + `active_backend` id — the
 * same data Prefs/CoreApi use) and talks to the mihomo external-controller with OkHttp:
 * mode switch = PATCH /configs {"mode": ...}. (The node-cycle call was removed in 1.4.14:
 * the widget's node row is display only, aligned with the HarmonyOS cards.)
 * Runs on a background thread from the provider (goAsync). Returns false on any failure.
 */
object WidgetActions {
    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
    }
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private data class BackendCfg(val baseUrl: String, val secret: String)

    /** Mirrors data.Backend.baseUrl (path prefix trimmed, IPv6 hosts bracketed). */
    private fun activeBackend(ctx: Context): BackendCfg? = runCatching {
        val sp = ctx.getSharedPreferences("clash_panel", Context.MODE_PRIVATE)
        val arr = JSONArray(sp.getString("backends", "[]") ?: "[]")
        if (arr.length() == 0) return null
        val activeId = sp.getString("active_backend", null)
        var chosen: JSONObject? = null
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (chosen == null) chosen = o
            if (o.optString("id") == activeId) { chosen = o; break }
        }
        val b = chosen ?: return null
        val protocol = b.optString("protocol", "http").ifBlank { "http" }
        val host = b.optString("host", "127.0.0.1")
        val port = b.optString("port", "9090")
        val path = b.optString("secondaryPath", "").trim().trim('/')
        val h = if (host.contains(':') && !host.startsWith("[")) "[$host]" else host
        BackendCfg("$protocol://$h:$port" + if (path.isEmpty()) "" else "/$path", b.optString("secret", ""))
    }.getOrNull()

    private fun request(cfg: BackendCfg, path: String): Request.Builder {
        val rb = Request.Builder().url(cfg.baseUrl + path).header("User-Agent", "MimiPanel-Widget")
        if (cfg.secret.isNotBlank()) rb.header("Authorization", "Bearer ${cfg.secret}")
        return rb
    }

    /** PATCH /configs {"mode": mode}. True on a 2xx response. */
    fun setMode(ctx: Context, mode: String): Boolean = runCatching {
        val cfg = activeBackend(ctx) ?: return false
        val body = JSONObject().put("mode", mode).toString().toRequestBody(JSON)
        client.newCall(request(cfg, "/configs").patch(body).build()).execute().use { it.isSuccessful }
    }.getOrDefault(false)
}

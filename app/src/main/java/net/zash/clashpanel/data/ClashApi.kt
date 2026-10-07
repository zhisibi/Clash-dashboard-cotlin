package net.zash.clashpanel.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class ApiException(val code: Int, msg: String) : IOException(msg)

class ClashApi(val backend: Backend) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val jsonType = "application/json".toMediaType()

    private val client: OkHttpClient = sharedClient.newBuilder()
        .addInterceptor { chain ->
            val b = chain.request().newBuilder()
            if (backend.secret.isNotEmpty()) b.header("Authorization", "Bearer ${backend.secret}")
            chain.proceed(b.build())
        }
        .build()

    private fun url(path: String): String = backend.baseUrl + path
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private suspend fun call(method: String, path: String, body: JsonElement? = null, timeoutMs: Long = 0): String =
        withContext(Dispatchers.IO) {
            val rb = body?.toString()?.toRequestBody(jsonType)
                ?: if (method == "GET" || method == "DELETE") null else "".toRequestBody(jsonType)
            val req = Request.Builder().url(url(path)).method(method, rb).build()
            val c = if (timeoutMs > 0) client.newBuilder().callTimeout(timeoutMs, TimeUnit.MILLISECONDS).build() else client
            c.newCall(req).execute().use { resp ->
                val text = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    val msg = runCatching { json.parseToJsonElement(text).jsonObject.s("message") }.getOrNull()
                    throw ApiException(resp.code, msg?.ifBlank { null } ?: "HTTP ${resp.code}")
                }
                text
            }
        }

    private suspend fun getObj(path: String): JsonObject =
        json.parseToJsonElement(call("GET", path).ifBlank { "{}" }).jsonObject

    // ---------- core ----------
    suspend fun version(): Pair<String, Boolean> {
        val o = getObj("/version")
        return o.s("version") to o.b("meta")
    }

    suspend fun configs(): CoreConfig {
        val o = getObj("/configs")
        val tun = o.obj("tun")
        return CoreConfig(
            mode = o.s("mode").lowercase().ifBlank { "rule" },
            logLevel = o.s("log-level").ifBlank { "info" },
            allowLan = o.b("allow-lan"),
            ipv6 = o.b("ipv6"),
            tunEnable = tun?.b("enable") ?: false,
            tunStack = tun?.s("stack") ?: "",
            mixedPort = o.i("mixed-port"),
            port = o.i("port"),
            socksPort = o.i("socks-port"),
            redirPort = o.i("redir-port"),
            tproxyPort = o.i("tproxy-port"),
            bindAddress = o.s("bind-address"),
            modes = o.arr("modes")?.mapNotNull { it.str()?.lowercase() }?.takeIf { it.isNotEmpty() }
                ?: listOf("rule", "global", "direct"),
        )
    }

    suspend fun patchConfigs(body: JsonObject) { call("PATCH", "/configs", body) }
    suspend fun reloadConfigs() { call("PUT", "/configs?force=true", buildJsonObject { put("path", ""); put("payload", "") }) }
    suspend fun restart() { call("POST", "/restart", JsonObject(emptyMap())) }
    suspend fun upgradeCore() { call("POST", "/upgrade", JsonObject(emptyMap()), 120_000) }
    suspend fun upgradeGeo() {
        try { call("POST", "/configs/geo", buildJsonObject { put("path", ""); put("payload", "") }, 120_000) }
        catch (e: ApiException) {
            if (e.code != 404 && e.code != 405) throw e
            call("POST", "/upgrade/geo", JsonObject(emptyMap()), 120_000)
        }
    }
    suspend fun flushFakeIp() { call("POST", "/cache/fakeip/flush") }
    suspend fun flushDns() { call("POST", "/cache/dns/flush") }

    // ---------- proxies ----------
    suspend fun proxies(): Map<String, Proxy> {
        val o = getObj("/proxies").obj("proxies") ?: return emptyMap()
        return o.mapNotNull { (k, v) -> (v as? JsonObject)?.let { k to parseProxy(k, it) } }.toMap()
    }

    suspend fun selectProxy(group: String, name: String) {
        call("PUT", "/proxies/${enc(group)}", buildJsonObject { put("name", name) })
    }

    suspend fun unfixProxy(group: String) { call("DELETE", "/proxies/${enc(group)}") }

    suspend fun proxyDelay(name: String, testUrl: String, timeout: Int): Int {
        val t = call("GET", "/proxies/${enc(name)}/delay?url=${enc(testUrl)}&timeout=$timeout", timeoutMs = timeout + 5000L)
        return json.parseToJsonElement(t).jsonObject.i("delay")
    }

    /** mihomo: GET /group/{name}/delay returns {proxyName: delay} */
    suspend fun groupDelay(name: String, testUrl: String, timeout: Int): Map<String, Int> {
        val t = call("GET", "/group/${enc(name)}/delay?url=${enc(testUrl)}&timeout=$timeout", timeoutMs = timeout + 30_000L)
        return json.parseToJsonElement(t).jsonObject.mapValues { (it.value as? JsonPrimitive)?.intOrNull ?: 0 }
    }

    suspend fun proxyProviders(): List<ProxyProvider> {
        val o = getObj("/providers/proxies").obj("providers") ?: return emptyList()
        return o.mapNotNull { (k, v) ->
            val p = v as? JsonObject ?: return@mapNotNull null
            val vt = p.s("vehicleType")
            if (vt.equals("Compatible", true)) return@mapNotNull null
            val sub = p.obj("subscriptionInfo")
            ProxyProvider(
                name = p.s("name").ifBlank { k }, type = p.s("type"), vehicleType = vt,
                proxies = p.arr("proxies")?.mapNotNull { e -> (e as? JsonObject)?.let { parseProxy(it.s("name"), it) } } ?: emptyList(),
                updatedAt = p["updatedAt"].str(), testUrl = p["testUrl"].str(),
                subUpload = sub?.l("Upload") ?: 0, subDownload = sub?.l("Download") ?: 0,
                subTotal = sub?.l("Total") ?: 0, subExpire = sub?.l("Expire") ?: 0,
            )
        }
    }

    suspend fun updateProxyProvider(name: String) { call("PUT", "/providers/proxies/${enc(name)}", timeoutMs = 60_000) }
    suspend fun healthcheckProvider(name: String) { call("GET", "/providers/proxies/${enc(name)}/healthcheck", timeoutMs = 60_000) }

    // ---------- rules ----------
    suspend fun rules(): List<Rule> {
        val a = getObj("/rules").arr("rules") ?: return emptyList()
        return a.mapIndexedNotNull { idx, e ->
            val o = e as? JsonObject ?: return@mapIndexedNotNull null
            val extra = o.obj("extra")
            Rule(
                index = if (o.containsKey("index")) o.i("index") else idx,
                type = o.s("type"), payload = o.s("payload"), proxy = o.s("proxy"),
                size = if (o.containsKey("size")) o.i("size") else -1,
                disabled = extra?.b("disabled") ?: false,
                hitCount = extra?.l("hitCount") ?: 0,
            )
        }
    }

    /** mihomo >= 1.19.x: PATCH /rules/disable {"index": true|false} */
    suspend fun setRuleDisabled(index: Int, disabled: Boolean) {
        call("PATCH", "/rules/disable", buildJsonObject { put(index.toString(), disabled) })
    }

    suspend fun ruleProviders(): List<RuleProvider> {
        val o = getObj("/providers/rules").obj("providers") ?: return emptyList()
        return o.mapNotNull { (k, v) ->
            val p = v as? JsonObject ?: return@mapNotNull null
            RuleProvider(
                name = p.s("name").ifBlank { k }, behavior = p.s("behavior"), format = p.s("format"),
                vehicleType = p.s("vehicleType"), ruleCount = p.i("ruleCount"), updatedAt = p["updatedAt"].str(),
            )
        }.sortedBy { it.name }
    }

    suspend fun updateRuleProvider(name: String) { call("PUT", "/providers/rules/${enc(name)}", timeoutMs = 60_000) }

    // ---------- connections ----------
    suspend fun closeConnection(id: String) { call("DELETE", "/connections/${enc(id)}") }
    suspend fun closeAllConnections() { call("DELETE", "/connections") }

    // ---------- websockets ----------
    private fun wsUrl(path: String): String {
        val http = url(path)
        return when {
            http.startsWith("https://") -> "wss://" + http.removePrefix("https://")
            http.startsWith("http://") -> "ws://" + http.removePrefix("http://")
            else -> http
        }
    }

    fun ws(path: String): Flow<String> = callbackFlow {
        var u = wsUrl(path)
        if (backend.secret.isNotEmpty()) {
            u += (if (u.contains('?')) "&" else "?") + "token=" + enc(backend.secret)
        }
        val req = Request.Builder().url(u.replaceFirst("ws", "http")).build()
        val socket = wsClient.newWebSocket(req, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) { trySend(text) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { close(t) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { close() }
        })
        awaitClose { socket.cancel() }
    }

    fun parse(text: String): JsonElement = json.parseToJsonElement(text)

    companion object {
        val sharedClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
        val wsClient: OkHttpClient = sharedClient.newBuilder()
            .readTimeout(0, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()

        fun validate(b: Backend): Boolean = runCatching { b.baseUrl.toHttpUrl() }.isSuccess
    }
}

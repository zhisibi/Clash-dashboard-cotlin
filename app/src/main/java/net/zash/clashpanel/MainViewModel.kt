package net.zash.clashpanel

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.*
import net.zash.clashpanel.data.*
import java.util.UUID

enum class ConnState { Idle, Connecting, Connected, Failed }

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)

    // ----- backend -----
    var backends by mutableStateOf(prefs.backends); private set
    var active by mutableStateOf(prefs.backends.firstOrNull { it.id == prefs.activeBackendId } ?: prefs.backends.firstOrNull()); private set
    var connState by mutableStateOf(ConnState.Idle); private set
    var connError by mutableStateOf<String?>(null); private set
    var version by mutableStateOf(""); private set
    var isMeta by mutableStateOf(false); private set
    var config by mutableStateOf(CoreConfig()); private set

    // ----- settings mirrored as state -----
    var theme by mutableStateOf(prefs.theme)
    var testUrl by mutableStateOf(prefs.testUrl)
    var testTimeout by mutableIntStateOf(prefs.testTimeout)
    var lowLatency by mutableIntStateOf(prefs.lowLatency)
    var mediumLatency by mutableIntStateOf(prefs.mediumLatency)
    var groupTestUrlFirst by mutableStateOf(prefs.groupTestUrlFirst)
    var hideUnavailable by mutableStateOf(prefs.hideUnavailable)
    var sortProxies by mutableStateOf(prefs.sortProxies)
    var proxyCols by mutableIntStateOf(prefs.proxyCols)
    var showGlobal by mutableStateOf(prefs.showGlobal)
    var showHiddenGroups by mutableStateOf(prefs.showHiddenGroups)
    var logMax by mutableIntStateOf(prefs.logMax)
    var closedConnMax by mutableIntStateOf(prefs.closedConnMax)
    var showRuleHits by mutableStateOf(prefs.showRuleHits)
    var cardAlpha by mutableFloatStateOf(prefs.cardAlpha)
    var wallpaperBlur by mutableFloatStateOf(prefs.wallpaperBlur)
    var wallpaperDim by mutableFloatStateOf(prefs.wallpaperDim)
    var wallpaper by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null); private set
    private val wallpaperFile get() = java.io.File(getApplication<Application>().filesDir, "wallpaper.img")

    // ----- proxies -----
    var proxies by mutableStateOf<Map<String, Proxy>>(emptyMap()); private set
    var providers by mutableStateOf<List<ProxyProvider>>(emptyList()); private set
    val testing = mutableStateMapOf<String, Boolean>()
    val nodeTesting = mutableStateMapOf<String, Boolean>()
    val manualDelay = mutableStateMapOf<String, Int>()
    var proxiesLoading by mutableStateOf(false); private set

    // ----- rules -----
    var rules by mutableStateOf<List<Rule>>(emptyList()); private set
    var ruleProviders by mutableStateOf<List<RuleProvider>>(emptyList()); private set
    val updatingRuleProviders = mutableStateMapOf<String, Boolean>()
    var rulesDisableSupported by mutableStateOf(true); private set
    /** Operations the current backend rejected with 404/405/501 (e.g. FlClash's restricted controller). */
    val unsupported = mutableStateMapOf<String, Int>()

    // ----- connections -----
    var activeConns by mutableStateOf<List<Connection>>(emptyList()); private set
    var closedConns by mutableStateOf<List<Connection>>(emptyList()); private set
    var connsPaused by mutableStateOf(false)
    var connTotalUp by mutableLongStateOf(0); private set
    var connTotalDown by mutableLongStateOf(0); private set
    var connMemory by mutableLongStateOf(0); private set
    private var prevConns: Map<String, Connection> = emptyMap()

    // ----- logs -----
    var logs by mutableStateOf<List<LogEntry>>(emptyList()); private set
    var logsPaused by mutableStateOf(false)
    var logLevel by mutableStateOf(prefs.logLevel); private set
    private var logSeq = 0L

    // ----- overview -----
    var traffic by mutableStateOf(Traffic()); private set
    var trafficHistory by mutableStateOf<List<Traffic>>(List(60) { Traffic() }); private set
    var memory by mutableLongStateOf(0); private set
    var memoryHistory by mutableStateOf<List<Long>>(List(60) { 0L }); private set

    val toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastFlow = toasts.asSharedFlow()

    private var api: ClashApi? = null
    private var wsJobs = mutableListOf<Job>()
    private var logJob: Job? = null

    init {
        active?.let { connect(it) }
        viewModelScope.launch { wallpaper = withContext(Dispatchers.IO) { loadWallpaper() } }
    }

    // ================= wallpaper =================
    private fun loadWallpaper(): androidx.compose.ui.graphics.ImageBitmap? {
        val f = wallpaperFile
        if (!f.exists()) return null
        return runCatching {
            val dm = getApplication<Application>().resources.displayMetrics
            val target = maxOf(dm.widthPixels, dm.heightPixels).coerceAtLeast(1080)
            val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(f.path, o)
            var sample = 1
            while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= target) sample *= 2
            val bmp = android.graphics.BitmapFactory.decodeFile(f.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
            // respect EXIF rotation
            val rot = runCatching {
                when (android.media.ExifInterface(f.path).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                    6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f
                }
            }.getOrDefault(0f)
            val out = if (rot != 0f) android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, android.graphics.Matrix().apply { postRotate(rot) }, true) else bmp
            out.asImageBitmap()
        }.getOrNull()
    }


    fun setWallpaper(uri: android.net.Uri) = launchSafe("设置壁纸") {
        withContext(Dispatchers.IO) {
            val app = getApplication<Application>()
            app.contentResolver.openInputStream(uri)!!.use { input -> wallpaperFile.outputStream().use { input.copyTo(it) } }
        }
        val bmp = withContext(Dispatchers.IO) { loadWallpaper() }
        if (bmp == null) { wallpaperFile.delete(); toast("无法读取这张图片") } else { wallpaper = bmp; toast("壁纸已更新") }
    }

    fun clearWallpaper() { wallpaperFile.delete(); wallpaper = null }

    private fun toast(s: String) { toasts.tryEmit(s) }

    private fun errMsg(e: Throwable): String = when (e) {
        is ApiException -> if (e.code == 401) "密钥错误 (401)" else e.message ?: "HTTP ${e.code}"
        is java.net.ConnectException -> "无法连接后端"
        is java.net.SocketTimeoutException -> "连接超时"
        else -> e.message ?: e.javaClass.simpleName
    }

    fun isUnsupported(key: String) = unsupported.containsKey(key)

    private fun launchSafe(label: String? = null, key: String? = label, block: suspend CoroutineScope.() -> Unit) =
        viewModelScope.launch {
            try { block() } catch (e: CancellationException) { throw e } catch (e: Throwable) {
                if (e is ApiException && e.code in setOf(404, 405, 501) && key != null) {
                    unsupported[key] = e.code
                    toast("${label ?: key}失败：当前后端不支持此接口 (HTTP ${e.code})")
                } else toast((label?.let { "${it}失败: " } ?: "") + errMsg(e))
            }
        }

    // ================= backend management =================
    fun saveBackend(b: Backend, activate: Boolean = true) {
        val list = backends.toMutableList()
        val i = list.indexOfFirst { it.id == b.id }
        if (i >= 0) list[i] = b else list.add(b)
        backends = list; prefs.backends = list
        if (activate || active?.id == b.id) connect(b)
    }

    fun newBackendId() = UUID.randomUUID().toString()

    fun deleteBackend(b: Backend) {
        val list = backends.filter { it.id != b.id }
        backends = list; prefs.backends = list
        if (active?.id == b.id) {
            val next = list.firstOrNull()
            if (next != null) connect(next) else disconnect()
        }
    }

    fun disconnect() {
        stopStreams()
        api = null; active = null; prefs.activeBackendId = null
        connState = ConnState.Idle
        proxies = emptyMap(); rules = emptyList(); activeConns = emptyList(); logs = emptyList()
    }

    fun connect(b: Backend) {
        stopStreams()
        active = b; prefs.activeBackendId = b.id
        val a = ClashApi(b); api = a
        connState = ConnState.Connecting; connError = null
        unsupported.clear()
        proxies = emptyMap(); providers = emptyList(); rules = emptyList(); ruleProviders = emptyList()
        activeConns = emptyList(); closedConns = emptyList(); logs = emptyList(); prevConns = emptyMap()
        trafficHistory = List(60) { Traffic() }; memoryHistory = List(60) { 0L }
        viewModelScope.launch {
            try {
                val (v, meta) = a.version()
                if (api !== a) return@launch
                version = v; isMeta = meta || v.contains("meta", true) || v.startsWith("v1.1") || v.startsWith("alpha")
                connState = ConnState.Connected
                startStreams(a)
                refreshAll()
            } catch (e: CancellationException) { throw e } catch (e: Throwable) {
                if (api !== a) return@launch
                connState = ConnState.Failed; connError = errMsg(e)
            }
        }
    }

    /** Test a backend without switching to it. */
    suspend fun probe(b: Backend): Result<String> = runCatching { ClashApi(b).version().first }

    fun retry() { active?.let { connect(it) } }

    fun refreshAll() {
        refreshConfig(); refreshProxies(); refreshRules()
    }

    // ================= streams =================
    private fun stopStreams() {
        wsJobs.forEach { it.cancel() }; wsJobs.clear()
        logJob?.cancel(); logJob = null
    }

    private fun startStreams(a: ClashApi) {
        wsJobs += stream(a, "/traffic") { e ->
            val o = e.jsonObject
            val t = Traffic(o.l("up"), o.l("down"), o.l("upTotal"), o.l("downTotal"))
            traffic = t
            trafficHistory = (trafficHistory + t).takeLast(60)
        }
        wsJobs += stream(a, "/memory") { e ->
            val m = e.jsonObject.l("inuse")
            memory = m
            memoryHistory = (memoryHistory + m).takeLast(60)
        }
        wsJobs += stream(a, "/connections?interval=1000") { e -> onConnections(e.jsonObject) }
        restartLogs(a)
    }

    private fun restartLogs(a: ClashApi) {
        logJob?.cancel()
        logJob = stream(a, "/logs?level=$logLevel") { e ->
            if (logsPaused) return@stream
            val o = e.jsonObject
            val entry = LogEntry(++logSeq, o.s("type"), o.s("payload"), System.currentTimeMillis())
            val l = logs
            logs = if (l.size >= logMax) (l.drop(l.size - logMax + 1) + entry) else l + entry
        }
    }

    private fun stream(a: ClashApi, path: String, onMsg: (JsonElement) -> Unit): Job = viewModelScope.launch {
        var backoff = 1000L
        while (isActive && api === a) {
            try {
                a.ws(path).collect { text ->
                    backoff = 1000L
                    val el = runCatching { a.parse(text) }.getOrNull() ?: return@collect
                    onMsg(el)
                }
            } catch (e: CancellationException) { throw e } catch (_: Throwable) { }
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(10_000L)
        }
    }

    private fun onConnections(o: JsonObject) {
        connTotalUp = o.l("uploadTotal"); connTotalDown = o.l("downloadTotal"); connMemory = o.l("memory")
        val list = o.arr("connections")?.mapNotNull { (it as? JsonObject)?.let(::parseConnection) } ?: emptyList()
        val prev = prevConns
        val now = System.currentTimeMillis()
        val withSpeed = list.map { c ->
            val p = prev[c.id]
            if (p != null) c.copy(uploadSpeed = (c.upload - p.upload).coerceAtLeast(0), downloadSpeed = (c.download - p.download).coerceAtLeast(0)) else c
        }
        val ids = withSpeed.mapTo(HashSet()) { it.id }
        val closedNow = prev.values.filter { it.id !in ids }.map { it.copy(uploadSpeed = 0, downloadSpeed = 0, closedAt = now) }
        prevConns = withSpeed.associateBy { it.id }
        if (closedNow.isNotEmpty()) closedConns = (closedNow + closedConns).take(closedConnMax)
        if (!connsPaused) activeConns = withSpeed
    }

    // ================= config =================
    fun refreshConfig() = launchSafe { api?.let { config = it.configs() } }

    fun setMode(mode: String) = launchSafe("切换模式", "patch") {
        val a = api ?: return@launchSafe
        a.patchConfigs(buildJsonObject { put("mode", mode) })
        config = config.copy(mode = mode)
        refreshProxies()
    }

    fun patchConfig(label: String, body: JsonObject, local: (CoreConfig) -> CoreConfig) = launchSafe(label, "patch") {
        val a = api ?: return@launchSafe
        a.patchConfigs(body)
        config = local(config)
        delay(300); config = a.configs()
    }

    fun setTun(enable: Boolean) = patchConfig("TUN", buildJsonObject { put("tun", buildJsonObject { put("enable", enable) }) }) { it.copy(tunEnable = enable) }
    fun setAllowLan(v: Boolean) = patchConfig("局域网", buildJsonObject { put("allow-lan", v) }) { it.copy(allowLan = v) }
    fun setIpv6(v: Boolean) = patchConfig("IPv6", buildJsonObject { put("ipv6", v) }) { it.copy(ipv6 = v) }
    fun setCoreLogLevel(v: String) = patchConfig("日志级别", buildJsonObject { put("log-level", v) }) { it.copy(logLevel = v) }
    fun setPort(key: String, v: Int) = patchConfig("端口", buildJsonObject { put(key, v) }) { it }

    fun coreAction(label: String, block: suspend (ClashApi) -> Unit) = launchSafe(label) {
        val a = api ?: return@launchSafe
        block(a)
        toast("$label 成功")
        if (label != "重启内核") { delay(500); refreshAll() } else { delay(2500); retry() }
    }

    // ================= proxies =================
    fun refreshProxies() = launchSafe {
        val a = api ?: return@launchSafe
        proxiesLoading = true
        try {
            coroutineScope {
                val p = async { a.proxies() }
                val pr = async { runCatching { a.proxyProviders() }.getOrDefault(emptyList()) }
                proxies = p.await(); providers = pr.await()
            }
            manualDelay.clear()
        } finally { proxiesLoading = false }
    }

    val groups: List<Proxy>
        get() {
            val all = proxies
            val global = all["GLOBAL"]
            val order = global?.all ?: emptyList()
            val idx = order.withIndex().associate { it.value to it.index }
            val gs = all.values.filter { it.isGroup && it.name != "GLOBAL" && (showHiddenGroups || !it.hidden) }
                .sortedBy { idx[it.name] ?: Int.MAX_VALUE }
            val withGlobal = if (global != null && showGlobal) listOf(global) + gs else gs
            return withGlobal
        }

    fun groupTestUrl(g: Proxy): String = if (groupTestUrlFirst && !g.testUrl.isNullOrBlank()) g.testUrl else testUrl

    fun delayOf(name: String, url: String?): Int {
        manualDelay[name]?.let { return it }
        val p = proxies[name] ?: return 0
        // a group shows the delay of its current selection
        if (p.isGroup && p.now != null && p.now != name) {
            val seen = HashSet<String>()
            var cur: Proxy? = p
            while (cur != null && cur.isGroup && cur.now != null && seen.add(cur.name)) {
                cur = proxies[cur.now]
            }
            return cur?.delayFor(url) ?: 0
        }
        return p.delayFor(url)
    }

    /** resolved final node of a chain of groups */
    fun resolveNow(name: String): String {
        val seen = HashSet<String>()
        var cur = proxies[name]
        while (cur != null && cur.isGroup && cur.now != null && seen.add(cur.name)) {
            val next = proxies[cur.now] ?: return cur.now
            cur = next
        }
        return cur?.name ?: name
    }

    fun nodesOf(g: Proxy): List<String> {
        var list = g.all
        val url = groupTestUrl(g)
        if (hideUnavailable) list = list.filter { n -> val p = proxies[n]; p == null || p.isGroup || delayOf(n, url) > 0 || p.history.isEmpty() }
        return when (sortProxies) {
            "latency" -> list.sortedBy { val d = delayOf(it, url); if (d <= 0) Int.MAX_VALUE else d }
            "name" -> list.sorted()
            else -> list
        }
    }

    fun select(group: Proxy, name: String) = launchSafe("切换节点") {
        val a = api ?: return@launchSafe
        if (!group.type.equals("Selector", true) && !group.type.equals("URLTest", true) && !group.type.equals("Fallback", true)) {
            toast("${group.type} 类型的代理组不能手动选择"); return@launchSafe
        }
        a.selectProxy(group.name, name)
        proxies = proxies.toMutableMap().also { it[group.name] = group.copy(now = name, fixed = if (group.type.equals("Selector", true)) null else name) }
        // close connections that use this group so the switch takes effect
        refreshProxies()
    }

    fun testGroup(g: Proxy) = launchSafe("测速") {
        val a = api ?: return@launchSafe
        if (testing[g.name] == true) return@launchSafe
        testing[g.name] = true
        try {
            val url = groupTestUrl(g)
            val ok = try {
                val res = a.groupDelay(g.name, url, testTimeout)
                g.all.forEach { n -> manualDelay[n] = res[n] ?: 0 }
                true
            } catch (e: ApiException) {
                // 5xx = every node timed out; 404 = core has no /group API -> test one by one
                if (e.code in 500..599) { g.all.forEach { n -> manualDelay[n] = 0 }; true } else false
            } catch (e: CancellationException) { throw e } catch (e: Throwable) { false }
            if (!ok) {
                val sem = Semaphore(8)
                coroutineScope {
                    g.all.filter { proxies[it]?.isGroup != true }.map { n ->
                        async { sem.withPermit { manualDelay[n] = runCatching { a.proxyDelay(n, url, testTimeout) }.getOrDefault(0) } }
                    }.awaitAll()
                }
            }
            val p = a.proxies(); proxies = p
        } finally { testing.remove(g.name) }
    }

    fun testNode(name: String, url: String) = launchSafe {
        val a = api ?: return@launchSafe
        nodeTesting[name] = true
        try { manualDelay[name] = runCatching { a.proxyDelay(name, url, testTimeout) }.getOrDefault(0) }
        finally { nodeTesting.remove(name) }
    }

    fun testAllGroups() = groups.filter { it.name != "GLOBAL" }.forEach { testGroup(it) }

    fun unfix(g: Proxy) = launchSafe("取消固定") { api?.unfixProxy(g.name); refreshProxies() }

    fun updateProvider(name: String) = launchSafe("更新订阅") { api?.updateProxyProvider(name); toast("$name 已更新"); refreshProxies() }
    fun healthcheck(name: String) = launchSafe("健康检查") { testing["provider:$name"] = true; try { api?.healthcheckProvider(name); refreshProxies() } finally { testing.remove("provider:$name") } }
    fun updateAllProviders() = providers.forEach { updateProvider(it.name) }

    // ================= rules =================
    fun refreshRules() = launchSafe {
        val a = api ?: return@launchSafe
        coroutineScope {
            val r = async { a.rules() }
            val rp = async { runCatching { a.ruleProviders() }.getOrDefault(emptyList()) }
            rules = r.await(); ruleProviders = rp.await()
        }
    }

    fun toggleRule(r: Rule) = launchSafe("切换规则") {
        val a = api ?: return@launchSafe
        try {
            a.setRuleDisabled(r.index, !r.disabled)
            rules = rules.map { if (it.index == r.index) it.copy(disabled = !r.disabled) else it }
        } catch (e: ApiException) {
            if (e.code == 404 || e.code == 405) { rulesDisableSupported = false; toast("当前内核不支持禁用单条规则") } else throw e
        }
    }

    fun updateRuleProvider(name: String) = launchSafe("更新规则集") {
        updatingRuleProviders[name] = true
        try { api?.updateRuleProvider(name); refreshRules() } finally { updatingRuleProviders.remove(name) }
    }

    fun updateAllRuleProviders() = ruleProviders.forEach { updateRuleProvider(it.name) }

    // ================= connections =================
    fun closeConn(id: String) = launchSafe("断开连接") { api?.closeConnection(id) }
    fun closeConns(ids: List<String>) = launchSafe("断开连接") { val a = api ?: return@launchSafe; ids.forEach { runCatching { a.closeConnection(it) } } }
    fun closeAll() = launchSafe("断开全部") { api?.closeAllConnections() }
    fun clearClosed() { closedConns = emptyList() }

    // ================= logs =================
    fun setLevel(l: String) {
        logLevel = l; prefs.logLevel = l
        api?.let { restartLogs(it) }
    }
    fun clearLogs() { logs = emptyList() }

    // ================= settings persistence =================
    fun savePrefs() {
        prefs.theme = theme; prefs.testUrl = testUrl; prefs.testTimeout = testTimeout
        prefs.lowLatency = lowLatency; prefs.mediumLatency = mediumLatency
        prefs.groupTestUrlFirst = groupTestUrlFirst; prefs.hideUnavailable = hideUnavailable
        prefs.sortProxies = sortProxies; prefs.proxyCols = proxyCols; prefs.showGlobal = showGlobal
        prefs.showHiddenGroups = showHiddenGroups; prefs.logMax = logMax; prefs.closedConnMax = closedConnMax
        prefs.showRuleHits = showRuleHits
        prefs.cardAlpha = cardAlpha; prefs.wallpaperBlur = wallpaperBlur; prefs.wallpaperDim = wallpaperDim
    }

    override fun onCleared() { stopStreams(); super.onCleared() }
}

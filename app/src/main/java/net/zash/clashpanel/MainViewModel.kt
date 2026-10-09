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
import net.zash.clashpanel.i18n.I18n
import net.zash.clashpanel.i18n.PRIVACY_VERSION
import net.zash.clashpanel.i18n.t
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

    // ----- appearance (1.1.3 – 1.1.5 of the HarmonyOS app) -----
    var accent by mutableStateOf(net.zash.clashpanel.ui.findAccent(prefs.str("accent", "blue")).key); private set
    var glassOn by mutableStateOf(prefs.bool("glass_on", false))
    var glassStyle by mutableStateOf(prefs.str("glass_style", "custom").let { if (it in listOf("custom", "thin", "regular", "thick")) it else "custom" })
    var glassBlur by mutableIntStateOf(prefs.int("glass_blur", 45))
    var glassAlpha by mutableFloatStateOf(prefs.float("glass_alpha", 0.55f))
    var glassRows by mutableStateOf(prefs.bool("glass_rows", false))
    var lightOn by mutableStateOf(prefs.bool("light_on", false)); private set
    var lightIntensity by mutableFloatStateOf(prefs.float("light_intensity", 0.6f))
    var lightTilt by mutableStateOf(prefs.bool("light_tilt", false)); private set
    var lightGlow by mutableStateOf(prefs.bool("light_glow", true))
    var lightSweep by mutableStateOf(prefs.bool("light_sweep", true))
    /** Light direction (degrees clockwise from the top); follows the gravity sensor when enabled */
    var lightDeg by mutableIntStateOf(330)
    var autoHideNav by mutableStateOf(prefs.bool("auto_hide_nav", true)); private set
    var navCollapsed by mutableStateOf(false); private set

    // ----- navigation -----
    var tab by mutableIntStateOf(0)
    /** Open settings sub-page: "" | lang | backend | panel | proxy | conn | crash | about */
    var settingsPage by mutableStateOf("")
    /** Legal document shown full screen: "" | privacy | agreement */
    var legalDoc by mutableStateOf("")
    var privacyAgreed by mutableStateOf(prefs.int("privacy_agreed", 0) >= PRIVACY_VERSION); private set
    private var pendingBackend: Backend? = null
    var crashPrompt by mutableStateOf(CrashLog.hasUnseen(app))
    /** Per-page UI state (filters, tabs…), kept across page swipes and language switches */
    val ui = UiState()
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

    private var api: CoreApi? = null
    private var wsJobs = mutableListOf<Job>()
    private var logJob: Job? = null

    init {
        I18n.init(prefs.str("lang", "system"))
        ui.connSort = prefs.connSort; ui.connDesc = prefs.connSortDesc
        // No network requests before the privacy policy / user agreement are accepted
        active?.let { if (privacyAgreed) connect(it) else pendingBackend = it }
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


    fun setWallpaper(uri: android.net.Uri) = launchSafe(t("wallpaper"), null) {
        withContext(Dispatchers.IO) {
            val app = getApplication<Application>()
            app.contentResolver.openInputStream(uri)!!.use { input -> wallpaperFile.outputStream().use { input.copyTo(it) } }
        }
        val bmp = withContext(Dispatchers.IO) { loadWallpaper() }
        if (bmp == null) { wallpaperFile.delete(); toast(t("wallpaper_read_failed")) } else { wallpaper = bmp; toast(t("wallpaper_updated")) }
    }

    fun clearWallpaper() { wallpaperFile.delete(); wallpaper = null }

    fun toast(s: String) { toasts.tryEmit(s) }

    // 1.2.7: About > Version update check
    var updateChecking by mutableStateOf(false); private set
    var updateFound by mutableStateOf<Updater.Release?>(null)
    fun checkUpdate() {
        if (updateChecking) return
        updateChecking = true
        viewModelScope.launch {
            try {
                val r = Updater.latest()
                if (Updater.cmp(r.version, BuildConfig.VERSION_NAME) > 0) updateFound = r
                else toast(t("update_latest", BuildConfig.VERSION_NAME))
            } catch (e: Exception) {
                toast(t("update_failed"))
            } finally { updateChecking = false }
        }
    }

    fun errMsg(e: Throwable): String = when (e) {
        is ApiException -> if (e.code == 401) t("err_401") else e.message ?: "HTTP ${e.code}"
        is java.net.ConnectException -> t("err_connect")
        is java.net.SocketTimeoutException -> t("err_timeout")
        is java.net.UnknownHostException -> t("err_dns")
        is java.net.UnknownServiceException -> t("err_cleartext")
        else -> e.message ?: e.javaClass.simpleName
    }

    fun isUnsupported(key: String) = unsupported.containsKey(key)

    /** Runs [block]; failures become a toast. 404/405/501 mark [key] as "not supported by this backend". Returns success. */
    private suspend fun run(label: String?, key: String?, block: suspend CoroutineScope.() -> Unit): Boolean =
        try { coroutineScope { block() }; true } catch (e: CancellationException) { throw e } catch (e: Throwable) {
            if (e is ApiException && e.code in setOf(404, 405, 501) && key != null) {
                unsupported[key] = e.code
                toast(t("op_failed_unsupported", label ?: key, e.code))
            } else toast(if (label != null) t("op_failed", label, errMsg(e)) else errMsg(e))
            false
        }

    private fun launchSafe(label: String? = null, key: String? = label, block: suspend CoroutineScope.() -> Unit) =
        viewModelScope.launch { run(label, key, block) }

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

    // ================= privacy consent =================
    fun agreePrivacy() {
        prefs.put("privacy_agreed", PRIVACY_VERSION)
        privacyAgreed = true
        val b = pendingBackend; pendingBackend = null
        if (b != null && connState == ConnState.Idle) connect(b)
    }

    /** Decline / withdraw: clear consent and stop all network activity (the activity then finishes). */
    fun declinePrivacy() {
        prefs.remove("privacy_agreed")
        privacyAgreed = false
        stopStreams(); api = null
        pendingBackend = active
        connState = ConnState.Idle
    }

    // ================= appearance =================
    fun setAccentKey(k: String) { accent = net.zash.clashpanel.ui.findAccent(k).key; prefs.put("accent", accent) }
    fun setLanguage(p: String) { prefs.put("lang", p); I18n.choose(p) }
    fun setLight(on: Boolean) { lightOn = on; if (!on) lightDeg = 330; savePrefs() }
    fun setLightTiltOn(on: Boolean) { lightTilt = on; if (!on) lightDeg = 330; savePrefs() }
    fun setAutoHide(on: Boolean) { autoHideNav = on; savePrefs(); if (!on) expandNav() }
    fun expandNav() { if (navCollapsed) navCollapsed = false }
    fun collapseNav() { if (autoHideNav && !navCollapsed) navCollapsed = true }

    fun disconnect() {
        stopStreams()
        api = null; active = null; prefs.activeBackendId = null
        connState = ConnState.Idle
        proxies = emptyMap(); rules = emptyList(); activeConns = emptyList(); logs = emptyList()
    }

    fun connect(b: Backend) {
        stopStreams()
        active = b; prefs.activeBackendId = b.id
        val a = CoreApi(b); api = a
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
    suspend fun probe(b: Backend): Result<String> = runCatching { CoreApi(b).version().first }

    fun retry() { active?.let { connect(it) } }

    fun refreshAll() {
        refreshConfig(); refreshProxies(); refreshRules()
    }

    // ================= pull to refresh (HarmonyOS 1.1.9 semantics) =================
    /**
     * Returns when the requests finish (success or failure) so the indicator can stay until then; failures toast.
     * Overview: probe + config/proxies/rules; proxies/rules: reload; connections/logs: probe then restart the streams;
     * a failed / connecting backend is reconnected once. No network before the privacy consent.
     */
    suspend fun pullRefresh(page: String) {
        if (!privacyAgreed) return
        val a = api
        if (a == null) { toast(if (active == null) t("not_connected_yet") else t("backend_not_connected")); return }
        if (connState != ConnState.Connected) {
            val b = active ?: return
            connect(b)
            val t0 = System.currentTimeMillis()
            while (connState == ConnState.Connecting && System.currentTimeMillis() - t0 < 8000) delay(150)
            if (connState != ConnState.Connected) toast(t("refresh_failed", connError ?: t("err_connect")))
            return
        }
        val L = t("act_refresh")
        when (page) {
            "proxies" -> run(L, null) { loadProxies(a) }
            "rules" -> run(L, null) { loadRules(a) }
            "connections", "logs" -> {
                if (run(L, null) { a.version() } && api === a) { stopStreams(); startStreams(a) }
            }
            else -> {
                val ok = run(L, null) { val v = a.version().first; if (api === a) version = v }
                if (!ok || api !== a) return
                coroutineScope {
                    launch { run(L, null) { config = a.configs() } }
                    launch { run(L, null) { loadProxies(a) } }
                    launch { run(L, null) { loadRules(a) } }
                }
                if (api === a && wsJobs.isEmpty()) startStreams(a)
            }
        }
    }

    // ================= streams =================
    private fun stopStreams() {
        wsJobs.forEach { it.cancel() }; wsJobs.clear()
        logJob?.cancel(); logJob = null
    }

    private fun startStreams(a: CoreApi) {
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

    private fun restartLogs(a: CoreApi) {
        logJob?.cancel()
        logJob = stream(a, "/logs?level=$logLevel") { e ->
            if (logsPaused) return@stream
            val o = e.jsonObject
            val entry = LogEntry(++logSeq, o.s("type"), o.s("payload"), System.currentTimeMillis())
            val l = logs
            logs = if (l.size >= logMax) (l.drop(l.size - logMax + 1) + entry) else l + entry
        }
    }

    private fun stream(a: CoreApi, path: String, onMsg: (JsonElement) -> Unit): Job = viewModelScope.launch {
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

    fun setMode(mode: String) = launchSafe(t("act_mode"), "patch") {
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
    fun setAllowLan(v: Boolean) = patchConfig(t("act_lan"), buildJsonObject { put("allow-lan", v) }) { it.copy(allowLan = v) }
    fun setIpv6(v: Boolean) = patchConfig("IPv6", buildJsonObject { put("ipv6", v) }) { it.copy(ipv6 = v) }
    fun setCoreLogLevel(v: String) = patchConfig(t("act_loglevel"), buildJsonObject { put("log-level", v) }) { it.copy(logLevel = v) }
    fun setPort(key: String, v: Int) = patchConfig(t("act_port"), buildJsonObject { put(key, v) }) { it }

    /** Core maintenance op; key (reload | geo | fakeip | dns | upgrade | restart) also marks "unsupported"; label is t("op_" + key). */
    fun coreAction(key: String, block: suspend (CoreApi) -> Unit) {
        val label = t("op_$key")
        launchSafe(label, key) {
            val a = api ?: return@launchSafe
            block(a)
            toast(t("act_ok", label))
            if (key != "restart") { delay(500); refreshAll() } else { delay(2500); retry() }
        }
    }

    // ================= proxies =================
    fun refreshProxies() = launchSafe { api?.let { loadProxies(it) } }

    private suspend fun loadProxies(a: CoreApi) {
        proxiesLoading = true
        try {
            coroutineScope {
                val p = async { a.proxies() }
                val pr = async { runCatching { a.proxyProviders() }.getOrDefault(emptyList()) }
                val pv = p.await(); val prv = pr.await()
                if (api === a) { proxies = pv; providers = prv; manualDelay.clear() }
            }
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

    fun select(group: Proxy, name: String) = launchSafe(t("act_select")) {
        val a = api ?: return@launchSafe
        if (!group.type.equals("Selector", true) && !group.type.equals("URLTest", true) && !group.type.equals("Fallback", true)) {
            toast(t("group_not_selectable", group.type)); return@launchSafe
        }
        a.selectProxy(group.name, name)
        proxies = proxies.toMutableMap().also { it[group.name] = group.copy(now = name, fixed = if (group.type.equals("Selector", true)) null else name) }
        // close connections that use this group so the switch takes effect
        refreshProxies()
    }

    fun testGroup(g: Proxy) = launchSafe(t("act_test")) {
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

    /** 1.2.6: any proxy-group latency test running (drives the header test-all spinner) */
    val anyGroupTesting: Boolean get() = testing.any { it.value && !it.key.startsWith("provider:") }

    fun testAllGroups() = groups.filter { it.name != "GLOBAL" }.forEach { testGroup(it) }

    fun unfix(g: Proxy) = launchSafe(t("act_unfix")) { api?.unfixProxy(g.name); refreshProxies() }

    fun updateProvider(name: String) = launchSafe(t("act_update_sub")) { api?.updateProxyProvider(name); toast(t("updated_name", name)); refreshProxies() }
    fun healthcheck(name: String) = launchSafe(t("act_healthcheck")) { testing["provider:$name"] = true; try { api?.healthcheckProvider(name); refreshProxies() } finally { testing.remove("provider:$name") } }
    fun updateAllProviders() = providers.forEach { updateProvider(it.name) }

    // ================= rules =================
    fun refreshRules() = launchSafe { api?.let { loadRules(it) } }

    private suspend fun loadRules(a: CoreApi) = coroutineScope {
        val r = async { a.rules() }
        val rp = async { runCatching { a.ruleProviders() }.getOrDefault(emptyList()) }
        val rv = r.await(); val rpv = rp.await()
        if (api === a) { rules = rv; ruleProviders = rpv }
    }

    fun toggleRule(r: Rule) = launchSafe(t("act_toggle_rule")) {
        val a = api ?: return@launchSafe
        try {
            a.setRuleDisabled(r.index, !r.disabled)
            rules = rules.map { if (it.index == r.index) it.copy(disabled = !r.disabled) else it }
        } catch (e: ApiException) {
            if (e.code == 404 || e.code == 405) { rulesDisableSupported = false; toast(t("rule_disable_unsupported")) } else throw e
        }
    }

    fun updateRuleProvider(name: String) = launchSafe(t("act_update_ruleset")) {
        updatingRuleProviders[name] = true
        try { api?.updateRuleProvider(name); refreshRules() } finally { updatingRuleProviders.remove(name) }
    }

    fun updateAllRuleProviders() = ruleProviders.forEach { updateRuleProvider(it.name) }

    // ================= connections =================
    fun closeConn(id: String) = launchSafe(t("act_close_conn")) { api?.closeConnection(id) }
    fun closeConns(ids: List<String>) = launchSafe(t("act_close_conn")) { val a = api ?: return@launchSafe; ids.forEach { runCatching { a.closeConnection(it) } } }
    fun closeAll() = launchSafe(t("act_close_all")) { api?.closeAllConnections() }
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
        prefs.put("glass_on", glassOn); prefs.put("glass_style", glassStyle); prefs.put("glass_blur", glassBlur)
        prefs.put("glass_alpha", glassAlpha); prefs.put("glass_rows", glassRows)
        prefs.put("light_on", lightOn); prefs.put("light_intensity", lightIntensity); prefs.put("light_tilt", lightTilt)
        prefs.put("light_glow", lightGlow); prefs.put("light_sweep", lightSweep); prefs.put("auto_hide_nav", autoHideNav)
    }

    fun setConnSort(s: String, desc: Boolean) { ui.connSort = s; ui.connDesc = desc; prefs.connSort = s; prefs.connSortDesc = desc }

    override fun onCleared() { stopStreams(); super.onCleared() }
}

/** Per-page UI state hoisted out of the pages (headers and bodies are composed separately). */
class UiState {
    var proxyTab by mutableIntStateOf(0)
    var proxyQuery by mutableStateOf("")
    var openGroup by mutableStateOf<String?>(null)
    var connTab by mutableIntStateOf(0)
    var connQuery by mutableStateOf("")
    var connSort by mutableStateOf("start")
    var connDesc by mutableStateOf(true)
    var connSource by mutableStateOf("")
    var connCompact by mutableStateOf(false)
    var confirmClose by mutableStateOf(false)
    var logQuery by mutableStateOf("")
    var logType by mutableStateOf("")
    var logNewestFirst by mutableStateOf(true)
    var ruleTab by mutableIntStateOf(0)
    var ruleQuery by mutableStateOf("")
    var settingsQuery by mutableStateOf("")
}

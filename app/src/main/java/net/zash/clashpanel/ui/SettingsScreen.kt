package net.zash.clashpanel.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import net.zash.clashpanel.BuildConfig
import net.zash.clashpanel.ConnState
import net.zash.clashpanel.MainViewModel
import net.zash.clashpanel.data.Backend
import net.zash.clashpanel.i18n.I18n
import net.zash.clashpanel.i18n.LEGAL_CONTACT
import net.zash.clashpanel.i18n.LEGAL_DEVELOPER
import net.zash.clashpanel.i18n.t
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.material.icons.automirrored.outlined.Send
import kotlin.math.roundToInt

private data class SettingsSection(val key: String, val title: String, val desc: String, val icon: ImageVector, val keywords: String)

/** title / desc / keywords are i18n keys; search matches the keywords in both languages */
private val sections = listOf(
    SettingsSection("lang", "lang_title", "lang_desc", Icons.Outlined.Translate, "lang_keywords"),
    SettingsSection("backend", "sec_backend", "sec_backend_desc", Icons.Outlined.Storage, "sec_backend_kw"),
    SettingsSection("panel", "sec_panel", "sec_panel_desc", Icons.Outlined.Home, "sec_panel_kw"),
    SettingsSection("proxy", "sec_proxy", "sec_proxy_desc", Icons.AutoMirrored.Outlined.Send, "sec_proxy_kw"),
    SettingsSection("conn", "sec_conn", "sec_conn_desc", Icons.AutoMirrored.Outlined.CompareArrows, "sec_conn_kw"),
    SettingsSection("crash", "sec_crash", "sec_crash_desc", Icons.Outlined.BugReport, "sec_crash_kw"),
    SettingsSection("about", "sec_about", "sec_about_desc", Icons.Outlined.Info, "sec_about_kw"),
)

@Composable
fun SettingsHeader(vm: MainViewModel) {
    val ex = LocalExtra.current
    val page = vm.settingsPage
    if (page.isNotEmpty()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, t("cancel"), Modifier.size(34.dp).clip(CircleShape).clickable { vm.settingsPage = ""; vm.expandNav() }.padding(6.dp), tint = ex.text)
            Spacer(Modifier.width(4.dp))
            HeaderStatus(sections.firstOrNull { it.key == page }?.let { t(it.title) } ?: t("settings"), vm.connState,
                vm.active?.let { "${it.host}:${it.port}" } ?: t("not_connected"), modifier = Modifier.weight(1f))
        }
    } else {
        // settings home: title and search share one header row
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(t("settings"), fontSize = 18.sp, fontWeight = FontWeight.Medium, color = ex.text)
            Spacer(Modifier.width(12.dp))
            SearchBox(vm.ui.settingsQuery, { vm.ui.settingsQuery = it }, t("search_settings"), Modifier.weight(1f), dense = true)
        }
    }
}

@Composable
fun SettingsBody(vm: MainViewModel, top: Dp, bottom: Dp) {
    val ex = LocalExtra.current
    val page = vm.settingsPage
    val scroll = rememberScrollState()
    LaunchedEffect(page) { scroll.scrollTo(0) }
    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).imePadding()
            .padding(start = 14.dp, end = 14.dp, top = if (page.isEmpty()) top else top - 14.dp, bottom = bottom + 14.dp),
    ) {
        when (page) {
            "" -> {
                val q = vm.ui.settingsQuery.trim().lowercase()
                val shown = sections.filter { s -> q.isEmpty() || (I18n.both(s.title) + " " + I18n.both(s.desc) + " " + I18n.both(s.keywords)).lowercase().contains(q) }
                Card0(Modifier.fillMaxWidth()) {
                    shown.forEachIndexed { i, s ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.settingsPage = s.key; vm.expandNav() }.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(ex.chip), contentAlignment = Alignment.Center) {
                                Icon(s.icon, null, Modifier.size(24.dp), tint = ex.text)
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t(s.title), fontSize = 18.sp, color = ex.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(4.dp))
                                Text(if (s.key == "lang") I18n.optionLabel(I18n.pref) else t(s.desc), fontSize = 14.sp, color = ex.subtle)
                            }
                            if (s.key == "crash" && vm.crashPrompt) {
                                Box(Modifier.padding(end = 8.dp).size(8.dp).clip(CircleShape).background(ex.bad))
                            }
                            Icon(Icons.Outlined.ChevronRight, null, tint = ex.subtle)
                        }
                        if (i < shown.size - 1) HorizontalDivider(color = ex.outlineVariant)
                    }
                }
            }
            "lang" -> LanguagePage(vm)
            "backend" -> BackendPage(vm)
            "panel" -> PanelPage(vm)
            "proxy" -> ProxyPrefsPage(vm)
            "conn" -> ConnPrefsPage(vm)
            "crash" -> CrashLogPage { vm.crashPrompt = false }
            "about" -> AboutPage(vm)
        }
    }
}

// ---------------- language (1.2.0) ----------------
@Composable
private fun LanguagePage(vm: MainViewModel) {
    val ex = LocalExtra.current
    Group(t("lang_title")) {
        I18n.PREFS.forEach { k ->
            RowItem(I18n.optionLabel(k), if (k == "system") t("lang_current_system", I18n.optionLabel(I18n.systemLang())) else null,
                onClick = { if (I18n.pref != k) vm.setLanguage(k) }) {
                if (I18n.pref == k) Icon(Icons.Outlined.Check, null, tint = ex.accentUi)
            }
        }
    }
    Text(t("lang_note"), Modifier.padding(start = 6.dp, end = 6.dp, top = 10.dp), fontSize = 13.sp, color = ex.subtle)
}

// ---------------- about ----------------
@Composable
private fun AboutPage(vm: MainViewModel) {
    val ex = LocalExtra.current
    val act = LocalContext.current as? android.app.Activity
    var withdraw by remember { mutableStateOf(false) }
    Group(null) {
        RowItem(t("app_name_label"), t("app_name"))
        RowItem(t("version"), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        RowItem(t("developer"), "$LEGAL_DEVELOPER · $LEGAL_CONTACT")
        RowItem(t("compat_api"), t("compat_api_desc"))
    }
    Group(t("legal")) {
        RowItem(t("privacy_policy"), onClick = { vm.legalDoc = "privacy" }) { Icon(Icons.Outlined.ChevronRight, null, tint = ex.subtle) }
        RowItem(t("user_agreement"), onClick = { vm.legalDoc = "agreement" }) { Icon(Icons.Outlined.ChevronRight, null, tint = ex.subtle) }
        RowItem(t("withdraw"), t("withdraw_desc"), onClick = { withdraw = true }) { Icon(Icons.Outlined.ChevronRight, null, tint = ex.subtle) }
    }
    if (withdraw) {
        AlertDialog(
            onDismissRequest = { withdraw = false },
            title = { Text(t("withdraw_title")) }, text = { Text(t("withdraw_msg")) },
            confirmButton = { TextButton({ withdraw = false; vm.declinePrivacy(); act?.finishAndRemoveTask() }) { Text(t("withdraw_exit"), color = ex.bad) } },
            dismissButton = { TextButton({ withdraw = false }) { Text(t("cancel")) } },
        )
    }
}

// ---------------- reusable rows ----------------
@Composable
private fun Group(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    if (title != null) SectionTitle(title) else Spacer(Modifier.height(14.dp))
    Card0(Modifier.fillMaxWidth()) { Column(Modifier.padding(vertical = 4.dp), content = content) }
}

@Composable
private fun RowItem(title: String, desc: String? = null, onClick: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null) {
    val ex = LocalExtra.current
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = ex.text)
            if (!desc.isNullOrBlank()) Text(desc, fontSize = 13.sp, color = ex.subtle)
        }
        if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }
    }
}

@Composable
private fun SwitchRow(title: String, desc: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) =
    RowItem(title, desc, onClick = { onChange(!checked) }) { Switch(checked, onChange, colors = accentSwitchColors()) }

@Composable
private fun NumberRow(title: String, value: Int, suffix: String = "", onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    RowItem(title) {
        OutlinedTextField(
            text, { t -> text = t.filter { it.isDigit() }.take(6); text.toIntOrNull()?.let(onChange) },
            Modifier.width(120.dp), singleLine = true, suffix = { if (suffix.isNotEmpty()) Text(suffix) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}

// ---------------- backend page ----------------
@Composable
private fun BackendPage(vm: MainViewModel) {
    val ex = LocalExtra.current
    var editing by remember { mutableStateOf<Backend?>(null) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    Group(t("backend_list")) {
        vm.backends.forEach { b ->
            val isActive = vm.active?.id == b.id
            Row(Modifier.fillMaxWidth().clickable { if (!isActive) vm.connect(b) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(isActive, { vm.connect(b) }, colors = accentRadioColors())
                Column(Modifier.weight(1f)) {
                    Text(b.display, fontSize = 16.sp)
                    Text(b.baseUrl + if (b.secret.isNotEmpty()) " · 🔑" else "", fontSize = 12.sp, color = ex.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton({ editing = b }) { Icon(Icons.Outlined.Edit, t("edit_backend")) }
                IconButton({ confirm = t("delete_backend_q", b.display) to { vm.deleteBackend(b) } }) { Icon(Icons.Outlined.Delete, null) }
            }
        }
        RowItem(t("add_backend"), onClick = { editing = Backend(vm.newBackendId()) }) { Icon(Icons.Outlined.Add, null) }
    }

    if (vm.connState == ConnState.Failed) {
        Spacer(Modifier.height(10.dp))
        Card0(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ErrorOutline, null, tint = ex.bad)
                Spacer(Modifier.width(10.dp))
                Text(t("conn_failed", vm.connError ?: ""), Modifier.weight(1f), color = ex.bad)
                TextButton({ vm.retry() }) { Text(t("retry")) }
            }
        }
    }

    if (vm.connState == ConnState.Connected) {
        val c = vm.config
        Group(t("core")) {
            RowItem(t("version"), (vm.version.ifBlank { t("version_unknown") }) + if (vm.isMeta) " · Meta" else "")
            RowItem(t("proxy_mode")) {
                SegTabs(c.modes.map { modeName(it) }, c.modes.indexOf(c.mode).coerceAtLeast(0), { vm.setMode(c.modes[it]) }, dense = I18n.isEn)
            }
            RowItem(t("core_log_level")) {
                DropdownChip(c.logLevel, listOf("debug", "info", "warning", "error", "silent").map { it to it }, { vm.setCoreLogLevel(it) }, Modifier.width(130.dp))
            }
        }
        Group(t("network")) {
            vm.unsupported["patch"]?.let { code ->
                Text(t("patch_unsupported", code),
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 13.sp, color = LocalExtra.current.bad)
            }
            SwitchRow(t("tun_mode"), if (c.tunStack.isNotBlank()) t("tun_stack", c.tunStack) else null, c.tunEnable) { vm.setTun(it) }
            SwitchRow(t("allow_lan"), null, c.allowLan) { vm.setAllowLan(it) }
            SwitchRow("IPv6", null, c.ipv6) { vm.setIpv6(it) }
            if (c.bindAddress.isNotBlank()) RowItem(t("bind_address"), c.bindAddress)
            PortRow(t("port_mixed"), "mixed-port", c.mixedPort, vm)
            PortRow(t("port_http"), "port", c.port, vm)
            PortRow(t("port_socks"), "socks-port", c.socksPort, vm)
            PortRow(t("port_redir"), "redir-port", c.redirPort, vm)
            PortRow(t("port_tproxy"), "tproxy-port", c.tproxyPort, vm)
        }
        Group(t("maintenance")) {
            OpRow(vm, "reload", Icons.Outlined.RestartAlt) { vm.coreAction("reload") { it.reloadConfigs() } }
            OpRow(vm, "geo", Icons.Outlined.Public) { vm.coreAction("geo") { it.upgradeGeo() } }
            OpRow(vm, "fakeip", Icons.Outlined.CleaningServices) { vm.coreAction("fakeip") { it.flushFakeIp() } }
            OpRow(vm, "dns", Icons.Outlined.Dns) { vm.coreAction("dns") { it.flushDns() } }
            OpRow(vm, "upgrade", Icons.Outlined.SystemUpdateAlt) { confirm = t("op_upgrade_confirm") to { vm.coreAction("upgrade") { it.upgradeCore() } } }
            OpRow(vm, "restart", Icons.Outlined.PowerSettingsNew, ex.bad) { confirm = t("op_restart_confirm") to { vm.coreAction("restart") { it.restart() } } }
        }
    }

    editing?.let { b -> BackendDialog(vm, b, isNew = vm.backends.none { it.id == b.id }) { editing = null } }
    confirm?.let { (msg, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null }, title = { Text(msg) },
            confirmButton = { TextButton({ confirm = null; action() }) { Text(t("ok")) } },
            dismissButton = { TextButton({ confirm = null }) { Text(t("cancel")) } },
        )
    }
}


@Composable
private fun PortRow(title: String, key: String, value: Int, vm: MainViewModel) {
    var text by remember(value) { mutableStateOf(if (value == 0) "" else value.toString()) }
    RowItem(title) {
        OutlinedTextField(
            text, { t -> text = t.filter { it.isDigit() }.take(5) }, Modifier.width(120.dp), singleLine = true,
            placeholder = { Text(t("off")) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            trailingIcon = {
                if ((text.toIntOrNull() ?: 0) != value) IconButton({ vm.setPort(key, text.toIntOrNull() ?: 0) }) { Icon(Icons.Outlined.Check, t("save")) }
            },
        )
    }
}

@Composable
fun BackendForm(vm: MainViewModel, initial: Backend, onSaved: (() -> Unit)? = null, onCancel: (() -> Unit)? = null) {
    val ex = LocalExtra.current
    val scope = rememberCoroutineScope()
    var protocol by remember { mutableStateOf(initial.protocol) }
    var host by remember { mutableStateOf(initial.host) }
    var port by remember { mutableStateOf(initial.port) }
    var path by remember { mutableStateOf(initial.secondaryPath) }
    var secret by remember { mutableStateOf(initial.secret) }
    var label by remember { mutableStateOf(initial.label) }
    var showSecret by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    /** Field that failed validation ("host" | "port"), highlighted in red */
    var errField by remember { mutableStateOf("") }
    fun build() = initial.copy(protocol = protocol, host = host.trim(), port = port.trim(), secondaryPath = path.trim(), secret = secret, label = label.trim())

    /**
     * 1.2.1: the buttons are never disabled (Material's disabled state is 38% alpha, far below the contrast threshold);
     * validate on tap instead: toast the problem, highlight the field and show the reason above the buttons.
     */
    fun validate(): Boolean {
        val pn = port.trim().toIntOrNull()
        val (key, msg) = when {
            host.isBlank() -> "host" to t("err_host_required")
            port.isBlank() -> "port" to t("err_port_required")
            pn == null || pn < 1 || pn > 65535 -> "port" to t("err_port_invalid")
            else -> "" to ""
        }
        errField = key
        if (key.isNotEmpty()) { status = "✗ $msg"; vm.toast(msg); return false }
        return true
    }
    fun clearErr(key: String) { if (errField == key) { errField = ""; status = null } }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SegTabs(listOf("http", "https"), if (protocol == "https") 1 else 0, { protocol = if (it == 1) "https" else "http" })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(host, { host = it; clearErr("host") }, Modifier.weight(1f), label = { Text(t("f_host"), maxLines = 1) }, singleLine = true,
                isError = errField == "host")
            OutlinedTextField(port, { port = it.filter { c -> c.isDigit() }.take(5); clearErr("port") }, Modifier.width(110.dp), label = { Text(t("f_port"), maxLines = 1) }, singleLine = true,
                isError = errField == "port", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        OutlinedTextField(path, { path = it }, Modifier.fillMaxWidth(), label = { Text(t("f_path"), maxLines = 1) }, placeholder = { Text(t("optional"), maxLines = 1) }, singleLine = true)
        OutlinedTextField(
            secret, { secret = it }, Modifier.fillMaxWidth(), label = { Text(t("f_secret"), maxLines = 1) }, placeholder = { Text(t("optional"), maxLines = 1) }, singleLine = true,
            visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { IconButton({ showSecret = !showSecret }) { Icon(if (showSecret) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null) } },
        )
        OutlinedTextField(label, { label = it }, Modifier.fillMaxWidth(), label = { Text(t("f_label"), maxLines = 1) }, placeholder = { Text(t("optional"), maxLines = 1) }, singleLine = true)
        status?.let { Text(it, fontSize = 13.sp, color = if (it.startsWith("✓")) ex.good else ex.bad) }
        OutlinedButton({
            // While a test runs, taps are ignored instead of greying the button out
            if (busy || !validate()) return@OutlinedButton
            busy = true; status = null
            scope.launch {
                val r = vm.probe(build()); busy = false
                status = r.fold({ t("test_ok", it.ifBlank { t("no_version") }) }, { "✗ " + vm.errMsg(it) })
            }
        }, Modifier.fillMaxWidth()) {
            if (busy) { CircularProgressIndicator(Modifier.size(18.dp), color = ex.accentUi, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
            Text(t("test_conn"), maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onCancel != null) OutlinedButton(onCancel, Modifier.weight(1f)) { Text(t("cancel"), maxLines = 1) }
            Button({
                if (!validate()) return@Button
                vm.saveBackend(build()); onSaved?.invoke()
            }, Modifier.weight(1f), colors = primaryButtonColors()) {
                Text(t("save_connect"), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun BackendDialog(vm: MainViewModel, b: Backend, isNew: Boolean, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismiss) {
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(18.dp)) {
                Text(if (isNew) t("add_backend") else t("edit_backend"), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                BackendForm(vm, b, onSaved = onDismiss, onCancel = onDismiss)
            }
        }
    }
}


@Composable
private fun OpRow(vm: MainViewModel, key: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color? = null, onClick: () -> Unit) {
    val code = vm.unsupported[key]
    val ex = LocalExtra.current
    RowItem(t("op_$key"), code?.let { t("unsupported_http", it) }, onClick = onClick) {
        Icon(icon, null, tint = if (code != null) ex.subtle else tint ?: ex.text)
    }
}

// ---------------- wallpaper ----------------
@Composable
private fun WallpaperGroup(vm: MainViewModel) {
    val ex = LocalExtra.current
    val ctx = LocalContext.current
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) vm.setWallpaper(uri) }
    fun pick() = runCatching {
        picker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
    }.onFailure { toast(ctx, t("pick_image_failed")) }
    Group(t("wallpaper")) {
        val wp = vm.wallpaper
        if (wp != null) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().height(160.dp).clip(RoundedCornerShape(14.dp))) {
                androidx.compose.foundation.Image(wp, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            }
        }
        RowItem(if (wp == null) t("wallpaper_upload") else t("wallpaper_change"), t("wallpaper_desc"), onClick = { pick() }) { Icon(Icons.Outlined.Wallpaper, null, tint = ex.text) }
        if (wp != null) {
            if (!vm.glassOn) SliderRow(t("card_opacity"), vm.cardAlpha, 0.2f..1f, "${(vm.cardAlpha * 100).roundToInt()}%") { vm.cardAlpha = it; vm.savePrefs() }
            SliderRow(t("wallpaper_dim"), vm.wallpaperDim, 0f..0.7f, "${(vm.wallpaperDim * 100).roundToInt()}%") { vm.wallpaperDim = it; vm.savePrefs() }
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                SliderRow(t("wallpaper_blur"), vm.wallpaperBlur, 0f..30f, "${vm.wallpaperBlur.roundToInt()}") { vm.wallpaperBlur = it; vm.savePrefs() }
            } else RowItem(t("wallpaper_blur"), t("wallpaper_blur_na"))
            RowItem(t("wallpaper_remove"), onClick = { vm.clearWallpaper() }) { Icon(Icons.Outlined.Delete, null, tint = ex.bad) }
        }
    }
}

@Composable
private fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, label: String, onChange: (Float) -> Unit) {
    val ex = LocalExtra.current
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row { Text(title, fontSize = 16.sp, color = ex.text, modifier = Modifier.weight(1f)); Text(label, fontSize = 13.sp, color = ex.subtle) }
        Slider(value, onChange, valueRange = range, colors = accentSliderColors())
    }
}

// ---------------- panel page ----------------
private val THEME_KEYS = listOf("system", "light", "dark")
private val GLASS_KEYS = listOf("custom", "thin", "regular", "thick")

@Composable
private fun PanelPage(vm: MainViewModel) {
    val ex = LocalExtra.current
    var accentMenu by remember { mutableStateOf(false) }
    Group(t("appearance")) {
        RowItem(t("theme")) {
            SegTabs(THEME_KEYS.map { t("theme_$it") }, THEME_KEYS.indexOf(vm.theme).coerceAtLeast(0), { vm.theme = THEME_KEYS[it]; vm.savePrefs() }, dense = I18n.isEn)
        }
        RowItem(t("accent_color"), onClick = { accentMenu = true }) {
            Box {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(ex.accentFill))
                    Spacer(Modifier.width(8.dp))
                    Text(t(findAccent(vm.accent).nameKey), fontSize = 15.sp, color = ex.accent, maxLines = 1)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Outlined.KeyboardArrowDown, null, Modifier.size(18.dp), tint = ex.subtle)
                }
                DropdownMenu(accentMenu, { accentMenu = false }, Modifier.widthIn(min = if (I18n.isEn) 240.dp else 220.dp)) {
                    Text(t("accent_color"), Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = ex.text)
                    ACCENTS.forEach { a ->
                        val on = vm.accent == a.key
                        DropdownMenuItem(
                            text = { Text(t(a.nameKey), color = if (on) ex.accent else ex.text, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal, maxLines = 1) },
                            leadingIcon = { Box(Modifier.size(20.dp).clip(CircleShape).background(if (ex.dark) a.dark else a.light)) },
                            trailingIcon = { if (on) Icon(Icons.Outlined.Check, null, tint = ex.accentUi) },
                            onClick = { vm.setAccentKey(a.key); accentMenu = false },
                        )
                    }
                }
            }
        }
        // color preview
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(t("color_preview"), fontSize = 13.sp, color = ex.subtle)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t("preview_button"), Modifier.clip(RoundedCornerShape(16.dp)).background(ex.primary).padding(horizontal = 16.dp, vertical = 7.dp), fontSize = 14.sp, color = ex.onPrimary)
                Text(t("preview_tag"), Modifier.clip(RoundedCornerShape(16.dp)).background(ex.accentSoft).padding(horizontal = 12.dp, vertical = 7.dp), fontSize = 14.sp, color = ex.accent)
                Spacer(Modifier.weight(1f))
                Switch(true, null, colors = accentSwitchColors())
            }
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(ex.chip)) {
                Box(Modifier.fillMaxWidth(0.62f).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(ex.accentUi))
            }
        }
    }

    Group(t("nav_bar")) {
        SwitchRow(t("auto_hide_nav"), t("auto_hide_nav_desc"), vm.autoHideNav) { vm.setAutoHide(it) }
    }

    Group(t("glass")) {
        SwitchRow(t("glass"), t("glass_desc"), vm.glassOn) { vm.glassOn = it; vm.savePrefs() }
        if (vm.glassOn) {
            // title above the segments: four options (longer in English) don't fit beside the title
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t("material"), fontSize = 16.sp, color = ex.text)
                SegTabs(GLASS_KEYS.map { t("glass_$it") }, GLASS_KEYS.indexOf(vm.glassStyle).coerceAtLeast(0), { vm.glassStyle = GLASS_KEYS[it]; vm.savePrefs() }, dense = I18n.isEn)
            }
            SliderRow(t("blur_strength"), vm.glassBlur.toFloat(), 0f..100f, "${vm.glassBlur}") { vm.glassBlur = it.roundToInt(); vm.savePrefs() }
            SliderRow(t("card_opacity"), vm.glassAlpha, 0.1f..0.95f, "${(vm.glassAlpha * 100).roundToInt()}%") { vm.glassAlpha = (it * 100).roundToInt() / 100f; vm.savePrefs() }
            SwitchRow(t("glass_rows"), t("glass_rows_desc"), vm.glassRows) { vm.glassRows = it; vm.savePrefs() }
        }
        RowItem(t("blur_support"), if (BLUR_SUPPORTED) t("blur_support_yes", android.os.Build.VERSION.RELEASE) else t("blur_support_no", android.os.Build.VERSION.RELEASE))
    }

    Group(t("light")) {
        SwitchRow(t("light"), t("light_desc"), vm.lightOn) { vm.setLight(it) }
        if (vm.lightOn) {
            SliderRow(t("light_intensity"), vm.lightIntensity, 0f..1f, "${(vm.lightIntensity * 100).roundToInt()}%") { vm.lightIntensity = (it * 100).roundToInt() / 100f; vm.savePrefs() }
            SwitchRow(t("light_tilt"), t("light_tilt_desc"), vm.lightTilt) { vm.setLightTiltOn(it) }
            SwitchRow(t("light_glow"), t("light_glow_desc"), vm.lightGlow) { vm.lightGlow = it; vm.savePrefs() }
            SwitchRow(t("light_sweep"), t("light_sweep_desc"), vm.lightSweep) { vm.lightSweep = it; vm.savePrefs() }
        }
    }

    WallpaperGroup(vm)
}

@Composable
fun LineChart(series: List<Pair<List<Float>, Color>>, modifier: Modifier) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        val max = (series.flatMap { it.first }.maxOrNull() ?: 0f).coerceAtLeast(1f) * 1.15f
        for (i in 1..3) {
            val y = size.height * i / 4f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
        }
        series.forEach { (data, color) ->
            if (data.size < 2) return@forEach
            val step = size.width / (data.size - 1)
            val path = Path()
            data.forEachIndexed { i, v ->
                val x = i * step; val y = size.height - (v / max) * size.height
                if (i == 0) path.moveTo(x, y) else {
                    val px = (i - 1) * step; val py = size.height - (data[i - 1] / max) * size.height
                    val cx = (px + x) / 2
                    path.cubicTo(cx, py, cx, y, x, y)
                }
            }
            val fill = Path().apply { addPath(path); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.25f), color.copy(alpha = 0f))))
            drawPath(path, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

// ---------------- proxy prefs ----------------
private val SORT_KEYS = listOf("default", "latency", "name")

@Composable
private fun ProxyPrefsPage(vm: MainViewModel) {
    val ex = LocalExtra.current
    var url by remember { mutableStateOf(vm.testUrl) }
    Group(t("latency_test")) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            OutlinedTextField(url, { url = it; vm.testUrl = it.trim(); vm.savePrefs() }, Modifier.fillMaxWidth(), label = { Text(t("test_url")) }, singleLine = true)
        }
        NumberRow(t("test_timeout"), vm.testTimeout, "ms") { vm.testTimeout = it; vm.savePrefs() }
        SwitchRow(t("group_url_first"), null, vm.groupTestUrlFirst) { vm.groupTestUrlFirst = it; vm.savePrefs() }
        NumberRow(t("low_latency"), vm.lowLatency, "ms") { vm.lowLatency = it; vm.savePrefs() }
        NumberRow(t("medium_latency"), vm.mediumLatency, "ms") { vm.mediumLatency = it; vm.savePrefs() }
    }
    Group(t("group_display")) {
        RowItem(t("node_sort")) {
            SegTabs(SORT_KEYS.map { t("psort_$it") }, SORT_KEYS.indexOf(vm.sortProxies).coerceAtLeast(0), { vm.sortProxies = SORT_KEYS[it]; vm.savePrefs() }, dense = I18n.isEn)
        }
        RowItem(t("cards_per_row")) {
            SegTabs(listOf("1", "2", "3"), vm.proxyCols - 1, { vm.proxyCols = it + 1; vm.savePrefs() })
        }
        SwitchRow(t("hide_unavailable"), null, vm.hideUnavailable) { vm.hideUnavailable = it; vm.savePrefs() }
        SwitchRow(t("show_global"), null, vm.showGlobal) { vm.showGlobal = it; vm.savePrefs() }
        SwitchRow(t("show_hidden_groups"), t("show_hidden_desc"), vm.showHiddenGroups) { vm.showHiddenGroups = it; vm.savePrefs() }
    }
}

// ---------------- connection prefs ----------------
@Composable
private fun ConnPrefsPage(vm: MainViewModel) {
    Group(t("keep_counts")) {
        NumberRow(t("closed_keep"), vm.closedConnMax, t("unit_items")) { vm.closedConnMax = it.coerceAtLeast(10); vm.savePrefs() }
        NumberRow(t("logs_keep"), vm.logMax, t("unit_items")) { vm.logMax = it.coerceAtLeast(50); vm.savePrefs() }
    }
    Group(t("rules")) {
        SwitchRow(t("show_rule_hits"), t("needs_core_support"), vm.showRuleHits) { vm.showRuleHits = it; vm.savePrefs() }
    }
}

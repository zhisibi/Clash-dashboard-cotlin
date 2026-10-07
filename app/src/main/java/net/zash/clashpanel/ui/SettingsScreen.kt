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

private data class SettingsSection(val key: String, val title: String, val desc: String, val icon: ImageVector, val keywords: String)

private val sections = listOf(
    SettingsSection("backend", "后端", "当前后端、内核运维与网络监听", Icons.Outlined.Storage, "后端 内核 模式 tun 局域网 端口 重启 更新 geo dns fakeip 重载 配置 密钥"),
    SettingsSection("panel", "面板", "主题、应用信息与交互偏好", Icons.Outlined.Home, "主题 深色 浅色 版本 关于"),
    SettingsSection("proxy", "代理", "测速、代理组展示与图标", Icons.Outlined.Language, "测速 延迟 url 超时 排序 隐藏 卡片 global"),
    SettingsSection("conn", "连接", "连接与日志保留数量", Icons.AutoMirrored.Outlined.CompareArrows, "连接 日志 保留 数量"),
    SettingsSection("crash", "闪退日志", "查看、复制、分享应用崩溃记录", Icons.Outlined.BugReport, "闪退 崩溃 crash 日志 反馈 bug"),
)

@Composable
fun SettingsScreen(vm: MainViewModel, contentPadding: PaddingValues) {
    val ex = LocalExtra.current
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    BackHandler(page != null) { page = null }

    Column(Modifier.fillMaxSize().background(ex.bg)) {
        // header
        Column(Modifier.background(ex.card).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(44.dp)) {
                if (page != null) {
                    IconButton({ page = null }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                }
                Text(sections.firstOrNull { it.key == page }?.title ?: "设置", fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = if (page == null) 8.dp else 0.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.width(1.dp).height(20.dp).background(MaterialTheme.colorScheme.outline))
                Spacer(Modifier.width(10.dp))
                val dot = when (vm.connState) { ConnState.Connected -> ex.good; ConnState.Connecting -> ex.warn; ConnState.Failed -> ex.bad; else -> ex.subtle }
                Box(Modifier.size(9.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(8.dp))
                Text(vm.active?.let { "${it.host}:${it.port}" } ?: "未连接", fontSize = 15.sp, color = ex.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (page == null) {
                Spacer(Modifier.height(8.dp))
                SearchBox(query, { query = it }, "搜索设置", Modifier.fillMaxWidth())
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 14.dp, end = 14.dp, bottom = contentPadding.calculateBottomPadding() + 14.dp),
        ) {
            when (page) {
                null -> {
                    Text("设置", Modifier.padding(start = 8.dp, top = 24.dp, bottom = 16.dp), fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    val shown = sections.filter { s -> query.isBlank() || (s.title + s.desc + s.keywords).contains(query, true) }
                    Card0(Modifier.fillMaxWidth()) {
                        shown.forEachIndexed { i, s ->
                            Row(
                                Modifier.fillMaxWidth().clickable { page = s.key }.padding(horizontal = 18.dp, vertical = 18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(ex.chip), contentAlignment = Alignment.Center) {
                                    Icon(s.icon, null, Modifier.size(24.dp))
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(s.title, fontSize = 18.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Text(s.desc, fontSize = 14.sp, color = ex.subtle)
                                }
                                Icon(Icons.Outlined.ChevronRight, null, tint = ex.subtle)
                            }
                            if (i < shown.size - 1) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
                "backend" -> BackendPage(vm)
                "panel" -> PanelPage(vm)
                "proxy" -> ProxyPrefsPage(vm)
                "conn" -> ConnPrefsPage(vm)
                "crash" -> CrashLogPage()
            }
        }
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
            Text(title, fontSize = 16.sp)
            if (!desc.isNullOrBlank()) Text(desc, fontSize = 13.sp, color = ex.subtle)
        }
        trailing?.invoke()
    }
}

@Composable
private fun SwitchRow(title: String, desc: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) =
    RowItem(title, desc, onClick = { onChange(!checked) }) { Switch(checked, onChange) }

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

    Group("后端列表") {
        vm.backends.forEach { b ->
            val isActive = vm.active?.id == b.id
            Row(Modifier.fillMaxWidth().clickable { if (!isActive) vm.connect(b) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(isActive, { vm.connect(b) })
                Column(Modifier.weight(1f)) {
                    Text(b.display, fontSize = 16.sp)
                    Text(b.baseUrl + if (b.secret.isNotEmpty()) " · 🔑" else "", fontSize = 12.sp, color = ex.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton({ editing = b }) { Icon(Icons.Outlined.Edit, "编辑") }
                IconButton({ confirm = "删除后端 ${b.display}？" to { vm.deleteBackend(b) } }) { Icon(Icons.Outlined.Delete, "删除") }
            }
        }
        RowItem("添加后端", onClick = { editing = Backend(vm.newBackendId()) }) { Icon(Icons.Outlined.Add, null) }
    }

    if (vm.connState == ConnState.Failed) {
        Spacer(Modifier.height(10.dp))
        Card0(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ErrorOutline, null, tint = ex.bad)
                Spacer(Modifier.width(10.dp))
                Text("连接失败：${vm.connError}", Modifier.weight(1f), color = ex.bad)
                TextButton({ vm.retry() }) { Text("重试") }
            }
        }
    }

    if (vm.connState == ConnState.Connected) {
        val c = vm.config
        Group("内核") {
            RowItem("版本", (vm.version.ifBlank { "未知（客户端未提供版本号）" }) + if (vm.isMeta) " · Meta" else "")
            RowItem("代理模式") {
                SegTabs(c.modes.map { modeName(it) }, c.modes.indexOf(c.mode).coerceAtLeast(0), { vm.setMode(c.modes[it]) })
            }
            RowItem("内核日志级别") {
                DropdownChip(c.logLevel, listOf("debug", "info", "warning", "error", "silent").map { it to it }, { vm.setCoreLogLevel(it) }, Modifier.width(130.dp))
            }
        }
        Group("网络") {
            vm.unsupported["patch"]?.let { code ->
                Text("当前后端不允许修改配置 (HTTP $code)。模式、TUN、端口等需要在代理客户端 App 里修改。",
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 13.sp, color = LocalExtra.current.bad)
            }
            SwitchRow("TUN 模式", if (c.tunStack.isNotBlank()) "栈：${c.tunStack}" else null, c.tunEnable) { vm.setTun(it) }
            SwitchRow("允许局域网连接", null, c.allowLan) { vm.setAllowLan(it) }
            SwitchRow("IPv6", null, c.ipv6) { vm.setIpv6(it) }
            if (c.bindAddress.isNotBlank()) RowItem("绑定地址", c.bindAddress)
            PortRow("混合端口", "mixed-port", c.mixedPort, vm)
            PortRow("HTTP 端口", "port", c.port, vm)
            PortRow("SOCKS 端口", "socks-port", c.socksPort, vm)
            PortRow("透明代理 Redir", "redir-port", c.redirPort, vm)
            PortRow("TProxy 端口", "tproxy-port", c.tproxyPort, vm)
        }
        Group("运维") {
            OpRow(vm, "重载配置文件", Icons.Outlined.RestartAlt) { vm.coreAction("重载配置") { it.reloadConfigs() } }
            OpRow(vm, "更新 GEO 数据库", Icons.Outlined.Public) { vm.coreAction("更新 GEO") { it.upgradeGeo() } }
            OpRow(vm, "清空 FakeIP 缓存", Icons.Outlined.CleaningServices) { vm.coreAction("清空 FakeIP") { it.flushFakeIp() } }
            OpRow(vm, "清空 DNS 缓存", Icons.Outlined.Dns) { vm.coreAction("清空 DNS 缓存") { it.flushDns() } }
            OpRow(vm, "更新内核", Icons.Outlined.SystemUpdateAlt) { confirm = "确定升级内核？升级后内核会自动重启。" to { vm.coreAction("更新内核") { it.upgradeCore() } } }
            OpRow(vm, "重启内核", Icons.Outlined.PowerSettingsNew, ex.bad) { confirm = "确定重启内核？" to { vm.coreAction("重启内核") { it.restart() } } }
        }
    }

    editing?.let { b -> BackendDialog(vm, b, isNew = vm.backends.none { it.id == b.id }) { editing = null } }
    confirm?.let { (msg, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null }, title = { Text(msg) },
            confirmButton = { TextButton({ confirm = null; action() }) { Text("确定") } },
            dismissButton = { TextButton({ confirm = null }) { Text("取消") } },
        )
    }
}

private fun modeName(m: String) = when (m) { "rule" -> "规则"; "global" -> "全局"; "direct" -> "直连"; else -> m }

@Composable
private fun PortRow(title: String, key: String, value: Int, vm: MainViewModel) {
    var text by remember(value) { mutableStateOf(if (value == 0) "" else value.toString()) }
    RowItem(title) {
        OutlinedTextField(
            text, { t -> text = t.filter { it.isDigit() }.take(5) }, Modifier.width(120.dp), singleLine = true,
            placeholder = { Text("关闭") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            trailingIcon = {
                if ((text.toIntOrNull() ?: 0) != value) IconButton({ vm.setPort(key, text.toIntOrNull() ?: 0) }) { Icon(Icons.Outlined.Check, "保存") }
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
    fun build() = initial.copy(protocol = protocol, host = host.trim(), port = port.trim(), secondaryPath = path.trim(), secret = secret, label = label.trim())

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SegTabs(listOf("http", "https"), if (protocol == "https") 1 else 0, { protocol = if (it == 1) "https" else "http" })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(host, { host = it }, Modifier.weight(1f), label = { Text("地址", maxLines = 1) }, singleLine = true)
            OutlinedTextField(port, { port = it.filter { c -> c.isDigit() }.take(5) }, Modifier.width(110.dp), label = { Text("端口", maxLines = 1) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        OutlinedTextField(path, { path = it }, Modifier.fillMaxWidth(), label = { Text("二级路径", maxLines = 1) }, placeholder = { Text("可选", maxLines = 1) }, singleLine = true)
        OutlinedTextField(
            secret, { secret = it }, Modifier.fillMaxWidth(), label = { Text("密钥", maxLines = 1) }, placeholder = { Text("可选", maxLines = 1) }, singleLine = true,
            visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { IconButton({ showSecret = !showSecret }) { Icon(if (showSecret) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null) } },
        )
        OutlinedTextField(label, { label = it }, Modifier.fillMaxWidth(), label = { Text("备注名", maxLines = 1) }, placeholder = { Text("可选", maxLines = 1) }, singleLine = true)
        status?.let { Text(it, fontSize = 13.sp, color = if (it.startsWith("✓")) ex.good else ex.bad) }
        OutlinedButton({
            busy = true; status = null
            scope.launch {
                val r = vm.probe(build()); busy = false
                status = r.fold({ "✓ 连接成功，内核 $it" }, { "✗ ${it.message ?: "连接失败"}" })
            }
        }, Modifier.fillMaxWidth(), enabled = !busy && host.isNotBlank() && port.isNotBlank()) {
            if (busy) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
            Text("测试连接", maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onCancel != null) OutlinedButton(onCancel, Modifier.weight(1f)) { Text("取消", maxLines = 1) }
            Button({ vm.saveBackend(build()); onSaved?.invoke() }, Modifier.weight(1f), enabled = host.isNotBlank() && port.isNotBlank()) {
                Text("保存并连接", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun BackendDialog(vm: MainViewModel, b: Backend, isNew: Boolean, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismiss) {
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(18.dp)) {
                Text(if (isNew) "添加后端" else "编辑后端", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                BackendForm(vm, b, onSaved = onDismiss, onCancel = onDismiss)
            }
        }
    }
}

private val opKeys = mapOf("重载配置文件" to "重载配置", "更新 GEO 数据库" to "更新 GEO", "清空 FakeIP 缓存" to "清空 FakeIP",
    "清空 DNS 缓存" to "清空 DNS 缓存", "更新内核" to "更新内核", "重启内核" to "重启内核")

@Composable
private fun OpRow(vm: MainViewModel, title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color? = null, onClick: () -> Unit) {
    val code = vm.unsupported[opKeys[title] ?: title]
    val ex = LocalExtra.current
    RowItem(title, code?.let { "当前后端不支持 (HTTP $it)" }, onClick = onClick) {
        Icon(icon, null, tint = if (code != null) ex.subtle else tint ?: LocalContentColor.current)
    }
}

// ---------------- wallpaper ----------------
@Composable
private fun WallpaperGroup(vm: MainViewModel) {
    val ex = LocalExtra.current
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) vm.setWallpaper(uri) }
    fun pick() = picker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
    Group("壁纸") {
        val wp = vm.wallpaper
        if (wp != null) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().height(160.dp).clip(RoundedCornerShape(14.dp))) {
                androidx.compose.foundation.Image(wp, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            }
        }
        RowItem(if (wp == null) "上传壁纸" else "更换壁纸", "从相册选择一张图片作为面板背景", onClick = { pick() }) { Icon(Icons.Outlined.Wallpaper, null) }
        if (wp != null) {
            SliderRow("卡片不透明度", vm.cardAlpha, 0.2f..1f, "${(vm.cardAlpha * 100).toInt()}%") { vm.cardAlpha = it; vm.savePrefs() }
            SliderRow("壁纸暗化", vm.wallpaperDim, 0f..0.7f, "${(vm.wallpaperDim * 100).toInt()}%") { vm.wallpaperDim = it; vm.savePrefs() }
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                SliderRow("壁纸模糊", vm.wallpaperBlur, 0f..30f, "${vm.wallpaperBlur.toInt()}") { vm.wallpaperBlur = it; vm.savePrefs() }
            }
            RowItem("移除壁纸", onClick = { vm.clearWallpaper() }) { Icon(Icons.Outlined.Delete, null, tint = ex.bad) }
        }
    }
}

@Composable
private fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, label: String, onChange: (Float) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row { Text(title, fontSize = 16.sp, modifier = Modifier.weight(1f)); Text(label, fontSize = 13.sp, color = LocalExtra.current.subtle) }
        Slider(value, onChange, valueRange = range)
    }
}

// ---------------- panel page ----------------
@Composable
private fun PanelPage(vm: MainViewModel) {
    Group("外观") {
        RowItem("主题") {
            val opts = listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色")
            SegTabs(opts.map { it.second }, opts.indexOfFirst { it.first == vm.theme }.coerceAtLeast(0), { vm.theme = opts[it].first; vm.savePrefs() })
        }
    }
    WallpaperGroup(vm)
    Group("关于") {
        RowItem("Clash 面板", "版本 ${BuildConfig.VERSION_NAME} · Kotlin + Jetpack Compose 原生实现")
        RowItem("兼容内核", "mihomo (Clash.Meta) RESTful API，部分功能兼容原版 Clash")
    }
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
@Composable
private fun ProxyPrefsPage(vm: MainViewModel) {
    var url by remember { mutableStateOf(vm.testUrl) }
    Group("测速") {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            OutlinedTextField(url, { url = it; vm.testUrl = it.trim(); vm.savePrefs() }, Modifier.fillMaxWidth(), label = { Text("测速 URL") }, singleLine = true)
        }
        NumberRow("超时", vm.testTimeout, "ms") { vm.testTimeout = it; vm.savePrefs() }
        SwitchRow("优先使用代理组自带的测速 URL", null, vm.groupTestUrlFirst) { vm.groupTestUrlFirst = it; vm.savePrefs() }
        NumberRow("低延迟阈值（绿色）", vm.lowLatency, "ms") { vm.lowLatency = it; vm.savePrefs() }
        NumberRow("中延迟阈值（黄色）", vm.mediumLatency, "ms") { vm.mediumLatency = it; vm.savePrefs() }
    }
    Group("代理组展示") {
        RowItem("节点排序") {
            val opts = listOf("default" to "默认", "latency" to "延迟", "name" to "名称")
            SegTabs(opts.map { it.second }, opts.indexOfFirst { it.first == vm.sortProxies }.coerceAtLeast(0), { vm.sortProxies = opts[it].first; vm.savePrefs() })
        }
        RowItem("每行卡片数") {
            SegTabs(listOf("1", "2", "3"), vm.proxyCols - 1, { vm.proxyCols = it + 1; vm.savePrefs() })
        }
        SwitchRow("隐藏不可用节点", null, vm.hideUnavailable) { vm.hideUnavailable = it; vm.savePrefs() }
        SwitchRow("显示 GLOBAL 代理组", null, vm.showGlobal) { vm.showGlobal = it; vm.savePrefs() }
        SwitchRow("显示隐藏的代理组", "配置中 hidden: true 的代理组", vm.showHiddenGroups) { vm.showHiddenGroups = it; vm.savePrefs() }
    }
}

// ---------------- connection prefs ----------------
@Composable
private fun ConnPrefsPage(vm: MainViewModel) {
    Group("保留数量") {
        NumberRow("已关闭连接保留", vm.closedConnMax, "条") { vm.closedConnMax = it.coerceAtLeast(10); vm.savePrefs() }
        NumberRow("日志保留", vm.logMax, "条") { vm.logMax = it.coerceAtLeast(50); vm.savePrefs() }
    }
    Group("规则") {
        SwitchRow("显示规则命中次数", "需要内核支持", vm.showRuleHits) { vm.showRuleHits = it; vm.savePrefs() }
    }
}

package net.zash.clashpanel.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.zash.clashpanel.MainViewModel
import net.zash.clashpanel.data.LogEntry

// ============================== LOGS ==============================

@Composable
fun LogsScreen(vm: MainViewModel, contentPadding: PaddingValues) {
    val ex = LocalExtra.current
    val ctx = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var typeFilter by rememberSaveable { mutableStateOf("") }
    var newestFirst by rememberSaveable { mutableStateOf(true) }
    val regex = remember(query) { runCatching { Regex(query, RegexOption.IGNORE_CASE) }.getOrNull() }
    val list = remember(vm.logs, query, typeFilter, newestFirst) {
        val f = vm.logs.filter { l ->
            (typeFilter.isEmpty() || l.type.equals(typeFilter, true)) &&
                (query.isBlank() || (regex?.containsMatchIn(l.payload) ?: l.payload.contains(query, true)))
        }
        if (newestFirst) f.asReversed() else f
    }
    val state = rememberLazyListState()

    Column(Modifier.fillMaxSize().background(ex.bg)) {
        Column(Modifier.background(ex.card).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DropdownChip(vm.logLevel, listOf("debug", "info", "warning", "error", "silent").map { it to it }, { vm.setLevel(it) }, Modifier.width(140.dp))
                Spacer(Modifier.weight(1f))
                RoundIconButton(if (newestFirst) Icons.Outlined.VerticalAlignTop else Icons.Outlined.VerticalAlignBottom) { newestFirst = !newestFirst }
                Spacer(Modifier.width(6.dp))
                RoundIconButton(Icons.Outlined.Download) { exportLogs(ctx, list) }
                Spacer(Modifier.width(6.dp))
                RoundIconButton(Icons.Outlined.ContentCopy) {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("logs", list.joinToString("\n") { "${fmtClock(it.time)} [${it.type}] ${it.payload}" }))
                    Toast.makeText(ctx, "已复制 ${list.size} 条日志", Toast.LENGTH_SHORT).show()
                }
                Spacer(Modifier.width(6.dp))
                RoundIconButton(if (vm.logsPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, active = vm.logsPaused) { vm.logsPaused = !vm.logsPaused }
                Spacer(Modifier.width(6.dp))
                RoundIconButton(Icons.Outlined.Close) { vm.clearLogs() }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                DropdownChip(
                    if (typeFilter.isEmpty()) "全部" else typeFilter.uppercase(),
                    listOf("" to "全部", "debug" to "DEBUG", "info" to "INFO", "warning" to "WARNING", "error" to "ERROR"),
                    { typeFilter = it }, Modifier.width(150.dp),
                )
                Spacer(Modifier.width(8.dp))
                SearchBox(query, { query = it }, "搜索 | Regex", Modifier.weight(1f))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (list.isEmpty()) {
            Box(Modifier.padding(14.dp)) { EmptyCard(if (vm.logLevel == "silent") "日志级别为 silent" else "等待日志…") }
        } else {
            LazyColumn(
                state = state,
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = contentPadding.calculateBottomPadding() + 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list, key = { it.seq }) { l -> LogCard(l, list.size, newestFirst) }
            }
        }
    }
}

private fun levelColor(type: String, ex: ExtraColors): Color = when (type.lowercase()) {
    "debug" -> ex.subtle
    "info" -> Color(0xFF38BDF8)
    "warning", "warn" -> ex.warn
    "error" -> ex.bad
    else -> ex.subtle
}

@Composable
private fun LogCard(l: LogEntry, total: Int, newestFirst: Boolean) {
    val ex = LocalExtra.current
    Card0(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(String.format("%02d", l.seq % 100), fontSize = 13.sp, color = ex.subtle)
                Spacer(Modifier.width(16.dp))
                Text(l.type.uppercase(), fontSize = 13.sp, color = levelColor(l.type, ex))
                Spacer(Modifier.weight(1f))
                Text(fmtClock(l.time), fontSize = 13.sp, color = ex.subtle)
            }
            Spacer(Modifier.height(4.dp))
            SelectionContainer { Text(l.payload, fontSize = 15.sp, lineHeight = 21.sp) }
        }
    }
}

private fun exportLogs(ctx: Context, list: List<LogEntry>) {
    try {
        val name = "clash-logs-" + java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(java.util.Date()) + ".txt"
        val text = list.joinToString("\n") { "${fmtClock(it.time)} [${it.type}] ${it.payload}" }
        if (Build.VERSION.SDK_INT >= 29) {
            val cv = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)!!
            ctx.contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) }
        } else {
            val f = java.io.File(ctx.getExternalFilesDir(null), name)
            f.writeText(text)
        }
        Toast.makeText(ctx, "已保存到下载: $name", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(ctx, "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

// ============================== RULES ==============================

@Composable
fun RulesScreen(vm: MainViewModel, contentPadding: PaddingValues) {
    val ex = LocalExtra.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    val regex = remember(query) { runCatching { Regex(query, RegexOption.IGNORE_CASE) }.getOrNull() }
    fun match(vararg s: String) = query.isBlank() || s.any { regex?.containsMatchIn(it) ?: it.contains(query, true) }
    val providerMap = remember(vm.ruleProviders) { vm.ruleProviders.associateBy { it.name } }

    Column(Modifier.fillMaxSize().background(ex.bg)) {
        Column(Modifier.background(ex.card).padding(horizontal = 12.dp, vertical = 8.dp)) {
            SegTabs(listOf("规则 ${vm.rules.size}", "规则提供商 ${vm.ruleProviders.size}"), tab, { tab = it })
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SearchBox(query, { query = it }, "搜索 | Regex", Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Box {
                    RoundIconButton(Icons.Outlined.Tune) { menu = true }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("显示命中次数") }, onClick = { vm.showRuleHits = !vm.showRuleHits; vm.savePrefs() },
                            trailingIcon = { Checkbox(vm.showRuleHits, null) })
                        DropdownMenuItem(text = { Text("刷新") }, leadingIcon = { Icon(Icons.Outlined.Refresh, null) }, onClick = { menu = false; vm.refreshRules() })
                        if (vm.ruleProviders.isNotEmpty()) DropdownMenuItem(text = { Text("更新全部规则集") }, leadingIcon = { Icon(Icons.Outlined.Sync, null) },
                            onClick = { menu = false; vm.updateAllRuleProviders() })
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        val pad = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = contentPadding.calculateBottomPadding() + 14.dp)
        if (tab == 0) {
            val list = vm.rules.filter { match(it.payload, it.type, it.proxy) }
            if (list.isEmpty()) Box(Modifier.padding(14.dp)) { EmptyCard() }
            else LazyColumn(contentPadding = pad, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.index }) { r ->
                    Card0(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${r.index + 1}", fontSize = 14.sp, color = ex.subtle)
                                Spacer(Modifier.width(14.dp))
                                Text("${r.type} :", fontSize = 14.sp, color = ex.subtle)
                                Spacer(Modifier.width(10.dp))
                                Text(r.payload.ifBlank { "-" }, Modifier.weight(1f, fill = false), fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                val size = providerMap[r.payload]?.ruleCount ?: r.size
                                if (size > 0) { Spacer(Modifier.width(6.dp)); Text("($size)", fontSize = 13.sp, color = ex.subtle) }
                                if (r.type == "RuleSet" && providerMap.containsKey(r.payload)) {
                                    Spacer(Modifier.width(6.dp))
                                    if (vm.updatingRuleProviders[r.payload] == true) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    else Icon(Icons.Outlined.Sync, "更新", Modifier.size(18.dp).clickableNoRipple { vm.updateRuleProvider(r.payload) }, tint = ex.subtle)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (vm.rulesDisableSupported) {
                                    Switch(!r.disabled, { vm.toggleRule(r) }, Modifier.height(28.dp))
                                    Spacer(Modifier.width(10.dp))
                                }
                                ProxyChainChip(vm, r.proxy)
                                if (vm.showRuleHits && r.hitCount > 0) {
                                    Spacer(Modifier.weight(1f)); Text("命中 ${r.hitCount}", fontSize = 12.sp, color = ex.subtle)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val list = vm.ruleProviders.filter { match(it.name, it.behavior) }
            if (list.isEmpty()) Box(Modifier.padding(14.dp)) { EmptyCard() }
            else LazyColumn(contentPadding = pad, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.name }) { p ->
                    Card0(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(p.name, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                    Spacer(Modifier.width(6.dp)); Text("(${p.ruleCount})", fontSize = 13.sp, color = ex.subtle)
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Tag(p.behavior); Tag(p.vehicleType); if (p.format.isNotBlank()) Tag(p.format)
                                    Text("更新于 ${fmtAgo(net.zash.clashpanel.data.parseIsoMillis(p.updatedAt))}", fontSize = 12.sp, color = ex.subtle)
                                }
                            }
                            if (vm.updatingRuleProviders[p.name] == true) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else IconButton({ vm.updateRuleProvider(p.name) }) { Icon(Icons.Outlined.Sync, "更新") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProxyChainChip(vm: MainViewModel, proxy: String) {
    val ex = LocalExtra.current
    val p = vm.proxies[proxy]
    val final = vm.resolveNow(proxy)
    val fp = vm.proxies[final]
    Row(
        Modifier.background(ex.chip, androidx.compose.foundation.shape.RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        p?.icon?.let { ProxyIcon(it, 18); Spacer(Modifier.width(4.dp)) }
        Text(proxy, fontSize = 14.sp, maxLines = 1)
        if (final != proxy) {
            Text("  ›  ", fontSize = 14.sp, color = ex.subtle)
            fp?.icon?.let { ProxyIcon(it, 18); Spacer(Modifier.width(4.dp)) }
            Text(final, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp))
        }
        if (p != null) {
            val d = vm.delayOf(proxy, vm.testUrl)
            if (d > 0) {
                Spacer(Modifier.width(8.dp))
                Text("$d", fontSize = 13.sp, color = if (d <= vm.lowLatency) ex.good else if (d <= vm.mediumLatency) ex.warn else ex.bad)
            }
        }
    }
}

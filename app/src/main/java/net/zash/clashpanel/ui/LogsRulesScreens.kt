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
import net.zash.clashpanel.i18n.t
import androidx.compose.ui.unit.Dp
import net.zash.clashpanel.data.LogEntry

// ============================== LOGS ==============================

private fun logLine(l: LogEntry) = "${fmtClock(l.time)} [${l.type}] ${l.payload}"

private fun logList(vm: MainViewModel): List<LogEntry> {
    val ui = vm.ui
    val regex = runCatching { Regex(ui.logQuery, RegexOption.IGNORE_CASE) }.getOrNull()
    val f = vm.logs.filter { l ->
        (ui.logType.isEmpty() || l.type.equals(ui.logType, true)) &&
            (ui.logQuery.isBlank() || (regex?.containsMatchIn(l.payload) ?: l.payload.contains(ui.logQuery, true)))
    }
    return if (ui.logNewestFirst) f.asReversed() else f
}

@Composable
private fun rememberLogList(vm: MainViewModel): List<LogEntry> {
    val ui = vm.ui
    return remember(vm.logs, ui.logQuery, ui.logType, ui.logNewestFirst) { logList(vm) }
}

@Composable
fun LogsHeader(vm: MainViewModel) {
    val ctx = LocalContext.current
    val ui = vm.ui
    val save = rememberTextSaver()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DropdownChip(vm.logLevel, listOf("debug", "info", "warning", "error", "silent").map { it to it }, { vm.setLevel(it) }, Modifier.width(130.dp), dense = true)
            Spacer(Modifier.weight(1f))
            RoundIconButton(if (ui.logNewestFirst) Icons.Outlined.VerticalAlignTop else Icons.Outlined.VerticalAlignBottom, dense = true) { ui.logNewestFirst = !ui.logNewestFirst }
            RoundIconButton(Icons.Outlined.Download, dense = true) {
                val list = logList(vm)
                save("mimi-logs-" + java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date()) + ".txt", list.joinToString("\n") { logLine(it) })
            }
            RoundIconButton(Icons.Outlined.ContentCopy, dense = true) {
                val list = logList(vm)
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("logs", list.joinToString("\n") { logLine(it) }))
                Toast.makeText(ctx, t("copied_logs", list.size), Toast.LENGTH_SHORT).show()
            }
            RoundIconButton(if (vm.logsPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, active = vm.logsPaused, dense = true) { vm.logsPaused = !vm.logsPaused }
            RoundIconButton(Icons.Outlined.Close, dense = true) { vm.clearLogs() }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DropdownChip(
                if (ui.logType.isEmpty()) t("all") else ui.logType.uppercase(),
                listOf("" to t("all"), "debug" to "DEBUG", "info" to "INFO", "warning" to "WARNING", "error" to "ERROR"),
                { ui.logType = it }, Modifier.width(128.dp), dense = true,
            )
            SearchBox(ui.logQuery, { ui.logQuery = it }, t("search_regex"), Modifier.weight(1f), dense = true)
        }
    }
}

@Composable
fun LogsBody(vm: MainViewModel, top: Dp, bottom: Dp) {
    val list = rememberLogList(vm)
    if (list.isEmpty()) {
        ScrollableEmpty(top, bottom + 14.dp, if (vm.logLevel == "silent") t("log_silent") else t("log_waiting"))
    } else {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = top, bottom = bottom + 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(list, key = { it.seq }) { l -> LogCard(l) }
        }
    }
}

private fun levelColor(type: String, ex: ExtraColors): Color = when (type.lowercase()) {
    "debug" -> ex.subtle
    "info" -> ex.info
    "warning", "warn" -> ex.warn
    "error" -> ex.bad
    else -> ex.subtle
}

@Composable
private fun LogCard(l: LogEntry) {
    val ex = LocalExtra.current
    Card0(Modifier.fillMaxWidth(), kind = GlassKind.Row) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(String.format(java.util.Locale.US, "%02d", l.seq % 100), fontSize = 13.sp, color = ex.subtle)
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

// ============================== RULES ==============================

@Composable
fun RulesHeader(vm: MainViewModel) {
    val ui = vm.ui
    var menu by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SegTabs(listOf(t("seg_rules", vm.rules.size), t("seg_rule_providers", vm.ruleProviders.size)), ui.ruleTab, { ui.ruleTab = it }, dense = true)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SearchBox(ui.ruleQuery, { ui.ruleQuery = it }, t("search_regex"), Modifier.weight(1f), dense = true)
            Box {
                RoundIconButton(Icons.Outlined.Tune, dense = true) { menu = true }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text(t("show_hit_counts")) }, onClick = { vm.showRuleHits = !vm.showRuleHits; vm.savePrefs() },
                        trailingIcon = { Checkbox(vm.showRuleHits, null) })
                    DropdownMenuItem(text = { Text(t("act_refresh")) }, leadingIcon = { Icon(Icons.Outlined.Refresh, null) }, onClick = { menu = false; vm.refreshRules() })
                    if (vm.ruleProviders.isNotEmpty()) DropdownMenuItem(text = { Text(t("update_all_rulesets")) }, leadingIcon = { Icon(Icons.Outlined.Sync, null) },
                        onClick = { menu = false; vm.updateAllRuleProviders() })
                }
            }
        }
    }
}

@Composable
fun RulesBody(vm: MainViewModel, top: Dp, bottom: Dp) {
    val ex = LocalExtra.current
    val ui = vm.ui
    val tab = ui.ruleTab
    val query = ui.ruleQuery
    val regex = remember(query) { runCatching { Regex(query, RegexOption.IGNORE_CASE) }.getOrNull() }
    fun match(vararg s: String) = query.isBlank() || s.any { regex?.containsMatchIn(it) ?: it.contains(query, true) }
    val providerMap = remember(vm.ruleProviders) { vm.ruleProviders.associateBy { it.name } }
    run {
        val pad = PaddingValues(start = 14.dp, end = 14.dp, top = top, bottom = bottom + 14.dp)
        if (tab == 0) {
            val list = vm.rules.filter { match(it.payload, it.type, it.proxy) }
            if (list.isEmpty()) ScrollableEmpty(top, bottom + 14.dp, t("no_data"))
            else LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.index }) { r ->
                    Card0(Modifier.fillMaxWidth(), kind = GlassKind.Row) {
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
                                    else Icon(Icons.Outlined.Sync, t("act_update_ruleset"), Modifier.size(18.dp).clickableNoRipple { vm.updateRuleProvider(r.payload) }, tint = ex.subtle)
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
                                    Spacer(Modifier.weight(1f)); Text(t("hits_n", r.hitCount), fontSize = 12.sp, color = ex.subtle)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val list = vm.ruleProviders.filter { match(it.name, it.behavior) }
            if (list.isEmpty()) ScrollableEmpty(top, bottom + 14.dp, t("no_data"))
            else LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.name }) { p ->
                    Card0(Modifier.fillMaxWidth(), kind = GlassKind.Row) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(p.name, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                    Spacer(Modifier.width(6.dp)); Text("(${p.ruleCount})", fontSize = 13.sp, color = ex.subtle)
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Tag(p.behavior); Tag(p.vehicleType); if (p.format.isNotBlank()) Tag(p.format)
                                    Text(t("updated_ago", fmtAgo(net.zash.clashpanel.data.parseIsoMillis(p.updatedAt))), fontSize = 12.sp, color = ex.subtle)
                                }
                            }
                            if (vm.updatingRuleProviders[p.name] == true) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else IconButton({ vm.updateRuleProvider(p.name) }) { Icon(Icons.Outlined.Sync, t("act_update_ruleset")) }
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

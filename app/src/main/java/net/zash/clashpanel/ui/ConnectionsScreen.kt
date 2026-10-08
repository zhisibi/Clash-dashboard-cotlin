package net.zash.clashpanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.zash.clashpanel.MainViewModel
import net.zash.clashpanel.i18n.t
import androidx.compose.ui.unit.Dp
import net.zash.clashpanel.data.Connection

private val SORT_KEYS = listOf("host", "start", "download", "upload", "downloadSpeed", "uploadSpeed", "rule", "chains", "process", "source", "type")
private val SORT_LABELS = listOf("cs_host", "cs_start", "cs_download", "cs_upload", "cs_dlspeed", "cs_ulspeed", "cs_rule", "cs_chains", "cs_process", "cs_source", "cs_type")

/** Filtered + sorted connections for the current tab (shared by header count and body). */
private fun connList(vm: MainViewModel): List<Connection> {
    val ui = vm.ui
    val base = when (ui.connTab) { 0 -> vm.activeConns; 1 -> vm.closedConns; else -> vm.activeConns + vm.closedConns }
    val query = ui.connQuery
    val regex = runCatching { Regex(query, RegexOption.IGNORE_CASE) }.getOrNull()
    val filtered = base.filter { c ->
        (ui.connSource.isEmpty() || c.meta.sourceIP == ui.connSource) &&
            (query.isBlank() || run {
                val hay = listOf(c.meta.displayHost, c.meta.destinationIP, c.meta.sourceIP, c.rule, c.rulePayload, c.chains.joinToString(" "), c.meta.process, c.meta.network, c.meta.type).joinToString(" ")
                regex?.containsMatchIn(hay) ?: hay.contains(query, true)
            })
    }
    val cmp: Comparator<Connection> = when (ui.connSort) {
        "host" -> compareBy { it.meta.displayHost }
        "download" -> compareBy { it.download }
        "upload" -> compareBy { it.upload }
        "downloadSpeed" -> compareBy { it.downloadSpeed }
        "uploadSpeed" -> compareBy { it.uploadSpeed }
        "rule" -> compareBy { it.rule + it.rulePayload }
        "chains" -> compareBy { it.chains.joinToString() }
        "process" -> compareBy { it.meta.process }
        "source" -> compareBy { it.meta.sourceIP }
        "type" -> compareBy { it.meta.type + it.meta.network }
        else -> compareBy { it.startMillis }
    }
    return filtered.sortedWith(if (ui.connDesc) cmp.reversed() else cmp)
}

@Composable
private fun rememberConnList(vm: MainViewModel): List<Connection> {
    val ui = vm.ui
    return remember(vm.activeConns, vm.closedConns, ui.connTab, ui.connQuery, ui.connSort, ui.connDesc, ui.connSource) { connList(vm) }
}

@Composable
fun ConnectionsHeader(vm: MainViewModel) {
    val ex = LocalExtra.current
    val ui = vm.ui
    val list = rememberConnList(vm)
    val sources = remember(vm.activeConns.size, vm.closedConns.size) {
        (vm.activeConns + vm.closedConns).map { it.meta.sourceIP }.filter { it.isNotBlank() }.distinct().sorted()
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // first row: segments + up/down totals (compact, on the right)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SegTabs(listOf(t("seg_active", vm.activeConns.size), t("seg_closed", vm.closedConns.size), t("seg_all")), ui.connTab, { ui.connTab = it }, dense = true)
            Spacer(Modifier.weight(1f))
            Column(Modifier.padding(start = 6.dp), horizontalAlignment = Alignment.End) {
                Text("↑ ${fmtBytes(vm.connTotalUp)}", fontSize = 11.sp, color = ex.up, maxLines = 1)
                Text("↓ ${fmtBytes(vm.connTotalDown)}", fontSize = 11.sp, color = ex.down, maxLines = 1)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val si = SORT_KEYS.indexOf(ui.connSort).coerceAtLeast(0)
            DropdownChip(t(SORT_LABELS[si]), SORT_KEYS.mapIndexed { i, k -> k to t(SORT_LABELS[i]) }, { vm.setConnSort(it, ui.connDesc) }, Modifier.weight(1f), dense = true)
            Text(t("n_items", list.size), fontSize = 11.sp, color = ex.subtle, maxLines = 1)
            RoundIconButton(if (ui.connDesc) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward, dense = true) { vm.setConnSort(ui.connSort, !ui.connDesc) }
            RoundIconButton(if (ui.connCompact) Icons.Outlined.ViewHeadline else Icons.Outlined.ViewAgenda, active = ui.connCompact, dense = true) { ui.connCompact = !ui.connCompact }
            RoundIconButton(if (vm.connsPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, active = vm.connsPaused, dense = true) { vm.connsPaused = !vm.connsPaused }
            RoundIconButton(if (ui.connTab == 1) Icons.Outlined.DeleteSweep else Icons.Outlined.Close, dense = true) {
                if (ui.connTab == 1) vm.clearClosed() else ui.confirmClose = true
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DropdownChip(
                if (ui.connSource.isEmpty()) t("all_sources") else ui.connSource,
                listOf("" to t("all")) + sources.map { it to it }, { ui.connSource = it }, Modifier.width(128.dp), dense = true,
            )
            SearchBox(ui.connQuery, { ui.connQuery = it }, t("search_regex"), Modifier.weight(1f), Icons.Outlined.FilterAlt, dense = true)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsBody(vm: MainViewModel, top: Dp, bottom: Dp) {
    val ui = vm.ui
    val list = rememberConnList(vm)
    var detail by remember { mutableStateOf<Connection?>(null) }
    if (list.isEmpty()) {
        ScrollableEmpty(top, bottom + 14.dp, t("no_data"))
    } else {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = top, bottom = bottom + 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (ui.connCompact) 6.dp else 10.dp),
        ) {
            items(list, key = { it.id + it.closedAt }) { c -> ConnCard(vm, c, ui.connCompact) { detail = c } }
        }
    }

    if (ui.confirmClose) {
        val filtered = ui.connQuery.isNotBlank() || ui.connSource.isNotEmpty()
        AlertDialog(
            onDismissRequest = { ui.confirmClose = false },
            title = { Text(if (!filtered) t("close_all_q") else t("close_filtered_q", list.size)) },
            confirmButton = {
                TextButton({
                    ui.confirmClose = false
                    if (!filtered) vm.closeAll() else vm.closeConns(list.filter { it.closedAt == 0L }.map { it.id })
                }) { Text(t("close")) }
            },
            dismissButton = { TextButton({ ui.confirmClose = false }) { Text(t("cancel")) } },
        )
    }

    detail?.let { c ->
        ModalBottomSheet(onDismissRequest = { detail = null }, containerColor = sheetColor()) {
            NoBackdrop { ConnDetail(vm, c) { detail = null } }
        }
    }
}

@Composable
private fun ConnCard(vm: MainViewModel, c: Connection, compact: Boolean, onClick: () -> Unit) {
    val ex = LocalExtra.current
    val now = System.currentTimeMillis()
    Card0(Modifier.fillMaxWidth(), onClick = onClick, kind = GlassKind.Row) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = if (compact) 8.dp else 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${c.meta.displayHost}:${c.meta.destinationPort}", Modifier.weight(1f),
                    fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (c.closedAt == 0L) {
                    Icon(Icons.Outlined.Close, t("close"), Modifier.size(20.dp).clickableNoRipple { vm.closeConn(c.id) }, tint = ex.subtle)
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tag("${c.meta.type}|${c.meta.network.uppercase()}")
                Spacer(Modifier.width(6.dp))
                Text(
                    c.chains.reversed().joinToString(" → "), Modifier.weight(1f),
                    fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (!compact) {
                Spacer(Modifier.height(4.dp))
                Text(
                    (if (c.rulePayload.isNotBlank()) "${c.rule}: ${c.rulePayload}" else c.rule) +
                        (if (c.meta.process.isNotBlank()) " · ${c.meta.process}" else "") +
                        (if (c.meta.sourceIP.isNotBlank()) " · ${c.meta.sourceIP}" else ""),
                    fontSize = 12.sp, color = ex.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("↑ ${fmtBytes(c.upload)}", fontSize = 12.sp, color = ex.up)
                    if (c.uploadSpeed > 0) Text(" (${fmtSpeed(c.uploadSpeed)})", fontSize = 12.sp, color = ex.up)
                    Spacer(Modifier.width(10.dp))
                    Text("↓ ${fmtBytes(c.download)}", fontSize = 12.sp, color = ex.down)
                    if (c.downloadSpeed > 0) Text(" (${fmtSpeed(c.downloadSpeed)})", fontSize = 12.sp, color = ex.down)
                    Spacer(Modifier.weight(1f))
                    val end = if (c.closedAt > 0) c.closedAt else now
                    Text(fmtDuration(end - c.startMillis), fontSize = 12.sp, color = ex.subtle)
                }
            }
        }
    }
}

@Composable
private fun ConnDetail(vm: MainViewModel, c: Connection, onDone: () -> Unit) {
    val ex = LocalExtra.current
    val rows = listOf(
        t("kv_host") to c.meta.host, t("kv_sniff") to c.meta.sniffHost, t("kv_dst_ip") to c.meta.destinationIP,
        t("kv_dst_port") to c.meta.destinationPort, t("kv_remote") to c.meta.remoteDestination,
        t("kv_source") to "${c.meta.sourceIP}:${c.meta.sourcePort}", t("kv_inbound") to c.meta.inboundName, t("kv_inbound_user") to c.meta.inboundUser,
        t("kv_type") to "${c.meta.type} / ${c.meta.network}", t("kv_dns") to c.meta.dnsMode,
        t("kv_process") to c.meta.process, t("kv_process_path") to c.meta.processPath, "UID" to (c.meta.uid?.toString() ?: ""),
        t("kv_rule") to c.rule, t("kv_rule_payload") to c.rulePayload, t("kv_chains") to c.chains.reversed().joinToString(" → "),
        t("kv_upload") to fmtBytes(c.upload), t("kv_download") to fmtBytes(c.download),
        t("kv_start") to fmtClock(c.startMillis), "ID" to c.id,
    ).filter { it.second.isNotBlank() && it.second != ":" }
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t("conn_detail"), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (c.closedAt == 0L) Button({ vm.closeConn(c.id); onDone() }) { Text(t("close")) }
        }
        Spacer(Modifier.height(10.dp))
        Card0(Modifier.fillMaxWidth().weight(1f)) {
            SelectionContainer {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(14.dp)) {
                    rows.forEach { (k, v) ->
                        Row(Modifier.padding(vertical = 5.dp)) {
                            Text(k, Modifier.width(if (net.zash.clashpanel.i18n.I18n.isEn) 104.dp else 84.dp), fontSize = 13.sp, color = ex.subtle)
                            Text(v, fontSize = 13.sp, fontFamily = if (k == "ID") FontFamily.Monospace else FontFamily.Default)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    return this.clickable(interactionSource = src, indication = null, onClick = onClick)
}

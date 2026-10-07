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
import net.zash.clashpanel.data.Connection

private val sortFields = listOf(
    "host" to "主机", "start" to "连接时间", "download" to "下载量", "upload" to "上传量",
    "downloadSpeed" to "下载速度", "uploadSpeed" to "上传速度", "rule" to "规则", "chains" to "代理链",
    "process" to "进程", "source" to "来源 IP", "type" to "类型",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsScreen(vm: MainViewModel, contentPadding: PaddingValues) {
    val ex = LocalExtra.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(vm.prefs.connSort) }
    var desc by rememberSaveable { mutableStateOf(vm.prefs.connSortDesc) }
    var sourceFilter by rememberSaveable { mutableStateOf("") }
    var detail by remember { mutableStateOf<Connection?>(null) }
    var confirmClose by remember { mutableStateOf(false) }
    var compact by rememberSaveable { mutableStateOf(false) }

    val base = when (tab) { 0 -> vm.activeConns; 1 -> vm.closedConns; else -> vm.activeConns + vm.closedConns }
    val sources = remember(vm.activeConns.size, vm.closedConns.size) {
        (vm.activeConns + vm.closedConns).map { it.meta.sourceIP }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val regex = remember(query) { runCatching { Regex(query, RegexOption.IGNORE_CASE) }.getOrNull() }
    val list = remember(base, query, sort, desc, sourceFilter) {
        val filtered = base.filter { c ->
            (sourceFilter.isEmpty() || c.meta.sourceIP == sourceFilter) &&
                (query.isBlank() || run {
                    val hay = listOf(c.meta.displayHost, c.meta.destinationIP, c.meta.sourceIP, c.rule, c.rulePayload, c.chains.joinToString(" "), c.meta.process, c.meta.network, c.meta.type).joinToString(" ")
                    regex?.containsMatchIn(hay) ?: hay.contains(query, true)
                })
        }
        val cmp: Comparator<Connection> = when (sort) {
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
        filtered.sortedWith(if (desc) cmp.reversed() else cmp)
    }

    Column(Modifier.fillMaxSize().background(ex.bg)) {
        Column(Modifier.background(ex.card).padding(horizontal = 12.dp, vertical = 8.dp)) {
            SegTabs(listOf("活跃 ${vm.activeConns.size}", "已关闭 ${vm.closedConns.size}", "全部"), tab, { tab = it })
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                DropdownChip(sortFields.first { it.first == sort }.second, sortFields, { sort = it; vm.prefs.connSort = it }, Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                RoundIconButton(if (desc) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward) { desc = !desc; vm.prefs.connSortDesc = desc }
                Spacer(Modifier.width(6.dp))
                RoundIconButton(if (compact) Icons.Outlined.ViewHeadline else Icons.Outlined.ViewAgenda) { compact = !compact }
                Spacer(Modifier.width(6.dp))
                RoundIconButton(if (vm.connsPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, active = vm.connsPaused) { vm.connsPaused = !vm.connsPaused }
                Spacer(Modifier.width(6.dp))
                RoundIconButton(if (tab == 1) Icons.Outlined.DeleteSweep else Icons.Outlined.Close) {
                    if (tab == 1) vm.clearClosed() else confirmClose = true
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                DropdownChip(
                    if (sourceFilter.isEmpty()) "全部" else sourceFilter,
                    listOf("" to "全部") + sources.map { it to it }, { sourceFilter = it }, Modifier.width(150.dp),
                )
                Spacer(Modifier.width(8.dp))
                SearchBox(query, { query = it }, "搜索 | Regex", Modifier.weight(1f), Icons.Outlined.FilterAlt)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)) {
            Text("↑ ${fmtBytes(vm.connTotalUp)}", fontSize = 12.sp, color = ex.up)
            Spacer(Modifier.width(12.dp))
            Text("↓ ${fmtBytes(vm.connTotalDown)}", fontSize = 12.sp, color = ex.down)
            Spacer(Modifier.weight(1f))
            Text("${list.size} 条", fontSize = 12.sp, color = ex.subtle)
        }
        if (list.isEmpty()) {
            Box(Modifier.padding(horizontal = 14.dp)) { EmptyCard() }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = contentPadding.calculateBottomPadding() + 14.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
            ) {
                items(list, key = { it.id + it.closedAt }) { c -> ConnCard(vm, c, compact) { detail = c } }
            }
        }
    }

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text(if (query.isBlank() && sourceFilter.isEmpty()) "断开所有连接？" else "断开筛选出的 ${list.size} 条连接？") },
            confirmButton = {
                TextButton({
                    confirmClose = false
                    if (query.isBlank() && sourceFilter.isEmpty()) vm.closeAll() else vm.closeConns(list.filter { it.closedAt == 0L }.map { it.id })
                }) { Text("断开") }
            },
            dismissButton = { TextButton({ confirmClose = false }) { Text("取消") } },
        )
    }

    detail?.let { c ->
        ModalBottomSheet(onDismissRequest = { detail = null }, containerColor = MaterialTheme.colorScheme.background) {
            ConnDetail(vm, c) { detail = null }
        }
    }
}

@Composable
private fun ConnCard(vm: MainViewModel, c: Connection, compact: Boolean, onClick: () -> Unit) {
    val ex = LocalExtra.current
    val now = System.currentTimeMillis()
    Card0(Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = if (compact) 8.dp else 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${c.meta.displayHost}:${c.meta.destinationPort}", Modifier.weight(1f),
                    fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (c.closedAt == 0L) {
                    Icon(Icons.Outlined.Close, "断开", Modifier.size(20.dp).clickableNoRipple { vm.closeConn(c.id) }, tint = ex.subtle)
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
        "主机" to c.meta.host, "嗅探主机" to c.meta.sniffHost, "目标 IP" to c.meta.destinationIP,
        "目标端口" to c.meta.destinationPort, "远端目标" to c.meta.remoteDestination,
        "来源" to "${c.meta.sourceIP}:${c.meta.sourcePort}", "入站" to c.meta.inboundName, "入站用户" to c.meta.inboundUser,
        "类型" to "${c.meta.type} / ${c.meta.network}", "DNS 模式" to c.meta.dnsMode,
        "进程" to c.meta.process, "进程路径" to c.meta.processPath, "UID" to (c.meta.uid?.toString() ?: ""),
        "规则" to c.rule, "规则内容" to c.rulePayload, "代理链" to c.chains.reversed().joinToString(" → "),
        "上传" to fmtBytes(c.upload), "下载" to fmtBytes(c.download),
        "开始时间" to fmtClock(c.startMillis), "ID" to c.id,
    ).filter { it.second.isNotBlank() && it.second != ":" }
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("连接详情", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (c.closedAt == 0L) Button({ vm.closeConn(c.id); onDone() }) { Text("断开") }
        }
        Spacer(Modifier.height(10.dp))
        Card0(Modifier.fillMaxWidth().weight(1f)) {
            SelectionContainer {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(14.dp)) {
                    rows.forEach { (k, v) ->
                        Row(Modifier.padding(vertical = 5.dp)) {
                            Text(k, Modifier.width(84.dp), fontSize = 13.sp, color = ex.subtle)
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

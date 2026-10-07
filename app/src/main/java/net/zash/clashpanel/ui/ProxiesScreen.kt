package net.zash.clashpanel.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.zash.clashpanel.MainViewModel
import net.zash.clashpanel.data.Proxy
import net.zash.clashpanel.data.ProxyProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxiesScreen(vm: MainViewModel, contentPadding: PaddingValues) {
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openGroup by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    val ex = LocalExtra.current

    val regex = remember(query) { runCatching { Regex(query, RegexOption.IGNORE_CASE) }.getOrNull() }
    fun match(s: String) = query.isBlank() || (regex?.containsMatchIn(s) ?: s.contains(query, true))

    Column(Modifier.fillMaxSize().background(ex.bg)) {
        Column(Modifier.background(ex.card).padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (vm.providers.isNotEmpty()) {
                SegTabs(listOf("代理组 ${vm.groups.size}", "代理提供商 ${vm.providers.size}"), tab, { tab = it })
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SearchBox(query, { query = it }, if (tab == 0) "搜索代理组 | Regex" else "搜索提供商 | Regex", Modifier.weight(1f), Icons.Outlined.ViewAgenda)
                Spacer(Modifier.width(8.dp))
                Box {
                    RoundIconButton(Icons.Outlined.Tune) { menu = true }
                    ProxyOptionsMenu(vm, menu) { menu = false }
                }
                Spacer(Modifier.width(8.dp))
                if (tab == 0) RoundIconButton(Icons.Outlined.Bolt) { vm.testAllGroups() }
                else RoundIconButton(Icons.Outlined.Sync) { vm.updateAllProviders() }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (tab == 0) {
            val groups = vm.groups.filter { g -> match(g.name) || g.all.any { match(it) } }
                .let { l -> if (vm.showGlobal) l else l.filter { it.name != "GLOBAL" } }
            if (groups.isEmpty()) {
                Box(Modifier.padding(16.dp)) { EmptyCard(if (vm.proxiesLoading) "加载中…" else "暂无数据") }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(vm.proxyCols.coerceIn(1, 3)),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = contentPadding.calculateBottomPadding() + 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(groups, key = { it.name }) { g -> GroupCard(vm, g) { openGroup = g.name } }
                }
            }
        } else {
            val list = vm.providers.filter { p -> match(p.name) || p.proxies.any { match(it.name) } }
            ProvidersList(vm, list, contentPadding)
        }
    }

    val gName = openGroup
    if (gName != null) {
        val g = vm.proxies[gName]
        if (g == null) openGroup = null else {
            val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(onDismissRequest = { openGroup = null }, sheetState = sheet, containerColor = MaterialTheme.colorScheme.background) {
                GroupDetail(vm, g, query)
            }
        }
    }
}

@Composable
private fun ProxyOptionsMenu(vm: MainViewModel, open: Boolean, onDismiss: () -> Unit) {
    DropdownMenu(open, onDismiss) {
        Text("节点排序", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), fontSize = 12.sp, color = LocalExtra.current.subtle)
        listOf("default" to "默认", "latency" to "按延迟", "name" to "按名称").forEach { (k, l) ->
            DropdownMenuItem(
                text = { Text(l) }, onClick = { vm.sortProxies = k; vm.savePrefs() },
                trailingIcon = { if (vm.sortProxies == k) Icon(Icons.Outlined.Check, null) },
            )
        }
        HorizontalDivider()
        CheckItem("隐藏不可用节点", vm.hideUnavailable) { vm.hideUnavailable = it; vm.savePrefs() }
        CheckItem("显示 GLOBAL", vm.showGlobal) { vm.showGlobal = it; vm.savePrefs() }
        CheckItem("显示隐藏的代理组", vm.showHiddenGroups) { vm.showHiddenGroups = it; vm.savePrefs() }
        HorizontalDivider()
        Text("每行卡片数", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), fontSize = 12.sp, color = LocalExtra.current.subtle)
        Row(Modifier.padding(horizontal = 12.dp)) {
            (1..3).forEach { n ->
                FilterChip(vm.proxyCols == n, { vm.proxyCols = n; vm.savePrefs() }, { Text("$n") }, Modifier.padding(end = 6.dp))
            }
        }
        HorizontalDivider()
        DropdownMenuItem(text = { Text("刷新") }, leadingIcon = { Icon(Icons.Outlined.Refresh, null) }, onClick = { onDismiss(); vm.refreshProxies() })
    }
}

@Composable
private fun CheckItem(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    DropdownMenuItem(
        text = { Text(label) }, onClick = { onChange(!checked) },
        trailingIcon = { Checkbox(checked, { onChange(it) }) },
    )
}

@Composable
private fun GroupCard(vm: MainViewModel, g: Proxy, onClick: () -> Unit) {
    val ex = LocalExtra.current
    val url = vm.groupTestUrl(g)
    val alive = g.all.count { vm.delayOf(it, url) > 0 }
    val testing = vm.testing[g.name] == true
    Card0(Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(start = 16.dp, end = 14.dp, top = 14.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(g.name, fontSize = 19.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    Text("${g.type.uppercase()} · $alive/${g.all.size}", fontSize = 12.sp, color = ex.subtle, letterSpacing = 1.sp, maxLines = 1)
                }
                if (!g.icon.isNullOrBlank()) {
                    Spacer(Modifier.width(6.dp))
                    ProxyIcon(g.icon, 46)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (g.fixed != null) Icons.Outlined.PushPin else Icons.Outlined.ArrowCircleRight, null, Modifier.size(16.dp), tint = ex.subtle)
                Spacer(Modifier.width(6.dp))
                val now = g.now ?: ""
                vm.proxies[now]?.icon?.let { ProxyIcon(it, 18); Spacer(Modifier.width(4.dp)) }
                Text(now, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                LatencyBadge(vm.delayOf(g.name, url), vm.lowLatency, vm.mediumLatency, testing) { vm.testGroup(g) }
            }
        }
    }
}

@Composable
private fun GroupDetail(vm: MainViewModel, g: Proxy, query: String) {
    val ex = LocalExtra.current
    val url = vm.groupTestUrl(g)
    val testing = vm.testing[g.name] == true
    val nodes = vm.nodesOf(g)
    val selectable = g.type.equals("Selector", true) || g.type.equals("URLTest", true) || g.type.equals("Fallback", true)
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.88f)) {
        Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!g.icon.isNullOrBlank()) { ProxyIcon(g.icon, 34); Spacer(Modifier.width(10.dp)) }
            Column(Modifier.weight(1f)) {
                Text(g.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${g.type} · ${nodes.size} 个节点 · 当前 ${g.now ?: "-"}", fontSize = 12.sp, color = ex.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (g.fixed != null && !g.type.equals("Selector", true)) {
                TextButton({ vm.unfix(g) }) { Text("取消固定") }
            }
            RoundIconButton(Icons.Outlined.Bolt, active = testing) { vm.testGroup(g) }
        }
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(nodes, key = { it }) { n ->
                val p = vm.proxies[n]
                val sel = g.now == n
                val d = vm.delayOf(n, url)
                val border = if (sel) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent
                Card0(
                    Modifier.fillMaxWidth().border(2.dp, border, RoundedCornerShape(18.dp)),
                    onClick = { if (selectable) vm.select(g, n) },
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            p?.icon?.let { ProxyIcon(it, 18); Spacer(Modifier.width(4.dp)) }
                            Text(n, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Tag(p?.type ?: "?")
                                if (p?.udp == true) Tag("UDP")
                                if (p?.isGroup == true) Tag(p.now ?: "", ex.subtle)
                            }
                            LatencyBadge(d, vm.lowLatency, vm.mediumLatency, vm.nodeTesting[n] == true) {
                                if (p?.isGroup == true) vm.testGroup(p) else vm.testNode(n, url)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProvidersList(vm: MainViewModel, list: List<ProxyProvider>, contentPadding: PaddingValues) {
    val ex = LocalExtra.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(1),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = contentPadding.calculateBottomPadding() + 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(list, key = { it.name }) { p ->
            var expanded by remember { mutableStateOf(false) }
            Card0(Modifier.fillMaxWidth(), onClick = { expanded = !expanded }) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            Text("${p.vehicleType} · ${p.proxies.size} 个节点 · 更新于 ${fmtAgo(net.zash.clashpanel.data.parseIsoMillis(p.updatedAt))}", fontSize = 12.sp, color = ex.subtle)
                        }
                        IconButton({ vm.healthcheck(p.name) }) {
                            if (vm.testing["provider:${p.name}"] == true) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Outlined.Bolt, "健康检查")
                        }
                        if (p.vehicleType.equals("HTTP", true)) IconButton({ vm.updateProvider(p.name) }) { Icon(Icons.Outlined.Sync, "更新") }
                    }
                    if (p.subTotal > 0) {
                        val used = p.subUpload + p.subDownload
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (used.toFloat() / p.subTotal).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        )
                        Spacer(Modifier.height(4.dp))
                        val exp = if (p.subExpire > 0) " · 到期 " + java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date(p.subExpire * 1000)) else ""
                        Text("${fmtBytes(used)} / ${fmtBytes(p.subTotal)}$exp", fontSize = 12.sp, color = ex.subtle)
                    }
                    if (expanded) {
                        Spacer(Modifier.height(10.dp))
                        p.proxies.forEach { n ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(n.name, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Tag(n.type); Spacer(Modifier.width(6.dp))
                                LatencyBadge(vm.delayOf(n.name, p.testUrl), vm.lowLatency, vm.mediumLatency, vm.nodeTesting[n.name] == true) {
                                    vm.testNode(n.name, p.testUrl ?: vm.testUrl)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

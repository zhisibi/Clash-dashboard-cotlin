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
import net.zash.clashpanel.i18n.t
import androidx.compose.ui.unit.Dp
import net.zash.clashpanel.data.Proxy
import net.zash.clashpanel.data.ProxyProvider

private fun queryMatcher(query: String): (String) -> Boolean {
    val regex = runCatching { Regex(query, RegexOption.IGNORE_CASE) }.getOrNull()
    return { s -> query.isBlank() || (regex?.containsMatchIn(s) ?: s.contains(query, true)) }
}

@Composable
fun ProxiesHeader(vm: MainViewModel) {
    val ui = vm.ui
    var menu by remember { mutableStateOf(false) }
    Column {
        if (vm.providers.isNotEmpty()) {
            SegTabs(listOf(t("seg_groups", vm.groups.size), t("seg_providers", vm.providers.size)), ui.proxyTab, { ui.proxyTab = it }, dense = true)
            Spacer(Modifier.height(6.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SearchBox(ui.proxyQuery, { ui.proxyQuery = it }, if (ui.proxyTab == 0) t("search_groups") else t("search_providers"), Modifier.weight(1f), dense = true)
            Spacer(Modifier.width(6.dp))
            Box {
                RoundIconButton(Icons.Outlined.Tune, dense = true) { menu = true }
                ProxyOptionsMenu(vm, menu) { menu = false }
            }
            Spacer(Modifier.width(6.dp))
            if (ui.proxyTab == 0 || vm.providers.isEmpty()) RoundIconButton(Icons.Outlined.Bolt, dense = true) { vm.testAllGroups() }
            else RoundIconButton(Icons.Outlined.Sync, dense = true) { vm.updateAllProviders() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxiesBody(vm: MainViewModel, top: Dp, bottom: Dp) {
    val ui = vm.ui
    val query = ui.proxyQuery
    val match = remember(query) { queryMatcher(query) }
    val tab = if (vm.providers.isEmpty()) 0 else ui.proxyTab
    val pad = PaddingValues(start = 14.dp, end = 14.dp, top = top, bottom = bottom + 14.dp)

    if (tab == 0) {
        val groups = vm.groups.filter { g -> match(g.name) || g.all.any { match(it) } }
            .let { l -> if (vm.showGlobal) l else l.filter { it.name != "GLOBAL" } }
        if (groups.isEmpty()) {
            ScrollableEmpty(top, bottom + 14.dp, if (vm.proxiesLoading) t("loading") else t("no_data"))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(vm.proxyCols.coerceIn(1, 3)),
                contentPadding = pad,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(groups, key = { it.name }) { g -> GroupCard(vm, g) { ui.openGroup = g.name } }
            }
        }
    } else {
        val list = vm.providers.filter { p -> match(p.name) || p.proxies.any { match(it.name) } }
        if (list.isEmpty()) ScrollableEmpty(top, bottom + 14.dp, t("no_data")) else ProvidersList(vm, list, pad)
    }

    val gName = ui.openGroup
    if (gName != null) {
        val g = vm.proxies[gName]
        if (g == null) ui.openGroup = null else {
            val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(onDismissRequest = { ui.openGroup = null }, sheetState = sheet, containerColor = sheetColor()) {
                NoBackdrop { GroupDetail(vm, g, query) }
            }
        }
    }
}

/** Sheet background: palette sheet color, kept readable when glass makes it translucent. */
@Composable
fun sheetColor(): androidx.compose.ui.graphics.Color {
    val p = LocalPal.current
    return if (p.dark) androidx.compose.ui.graphics.Color(0xFF111316).copy(alpha = maxOf(p.sheet.alpha, 0.94f))
    else androidx.compose.ui.graphics.Color(0xFFF4F4F6).copy(alpha = maxOf(p.sheet.alpha, 0.94f))
}

@Composable
private fun ProxyOptionsMenu(vm: MainViewModel, open: Boolean, onDismiss: () -> Unit) {
    DropdownMenu(open, onDismiss) {
        Text(t("menu_node_sort"), Modifier.padding(horizontal = 16.dp, vertical = 6.dp), fontSize = 12.sp, color = LocalExtra.current.subtle)
        listOf("default" to t("sort_default"), "latency" to t("sort_by_latency"), "name" to t("sort_by_name")).forEach { (k, l) ->
            DropdownMenuItem(
                text = { Text(l) }, onClick = { vm.sortProxies = k; vm.savePrefs() },
                trailingIcon = { if (vm.sortProxies == k) Icon(Icons.Outlined.Check, null) },
            )
        }
        HorizontalDivider()
        Text(t("menu_display"), Modifier.padding(horizontal = 16.dp, vertical = 6.dp), fontSize = 12.sp, color = LocalExtra.current.subtle)
        CheckItem(t("hide_unavailable"), vm.hideUnavailable) { vm.hideUnavailable = it; vm.savePrefs() }
        CheckItem(t("show_global_short"), vm.showGlobal) { vm.showGlobal = it; vm.savePrefs() }
        CheckItem(t("show_hidden_groups"), vm.showHiddenGroups) { vm.showHiddenGroups = it; vm.savePrefs() }
        HorizontalDivider()
        Text(t("cards_per_row"), Modifier.padding(horizontal = 16.dp, vertical = 6.dp), fontSize = 12.sp, color = LocalExtra.current.subtle)
        Row(Modifier.padding(horizontal = 12.dp)) {
            (1..3).forEach { n ->
                FilterChip(vm.proxyCols == n, { vm.proxyCols = n; vm.savePrefs() }, { Text(t("n_cols", n)) }, Modifier.padding(end = 6.dp))
            }
        }
        HorizontalDivider()
        DropdownMenuItem(text = { Text(t("act_refresh")) }, leadingIcon = { Icon(Icons.Outlined.Refresh, null) }, onClick = { onDismiss(); vm.refreshProxies() })
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
                Text(t("group_detail_sub", g.type, nodes.size, g.now ?: "-"), fontSize = 12.sp, color = ex.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (g.fixed != null && !g.type.equals("Selector", true)) {
                TextButton({ vm.unfix(g) }) { Text(t("act_unfix")) }
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
                val border = if (sel) ex.accent else androidx.compose.ui.graphics.Color.Transparent
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
private fun ProvidersList(vm: MainViewModel, list: List<ProxyProvider>, pad: PaddingValues) {
    val ex = LocalExtra.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(1),
        contentPadding = pad,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(list, key = { it.name }) { p ->
            var expanded by remember { mutableStateOf(false) }
            Card0(Modifier.fillMaxWidth(), onClick = { expanded = !expanded }, kind = GlassKind.Row) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            Text(t("provider_sub", p.vehicleType, p.proxies.size, fmtAgo(net.zash.clashpanel.data.parseIsoMillis(p.updatedAt))), fontSize = 12.sp, color = ex.subtle)
                        }
                        IconButton({ vm.healthcheck(p.name) }) {
                            if (vm.testing["provider:${p.name}"] == true) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Outlined.Bolt, t("act_healthcheck"))
                        }
                        if (p.vehicleType.equals("HTTP", true)) IconButton({ vm.updateProvider(p.name) }) { Icon(Icons.Outlined.Sync, t("act_update_sub")) }
                    }
                    if (p.subTotal > 0) {
                        val used = p.subUpload + p.subDownload
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (used.toFloat() / p.subTotal).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        )
                        Spacer(Modifier.height(4.dp))
                        val exp = if (p.subExpire > 0) t("sub_expire", java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(p.subExpire * 1000))) else ""
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

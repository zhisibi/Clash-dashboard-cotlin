package net.zash.clashpanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.zash.clashpanel.ConnState
import net.zash.clashpanel.MainViewModel
import net.zash.clashpanel.i18n.t
import androidx.compose.ui.unit.Dp
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted

@Composable
fun OverviewBody(vm: MainViewModel, top: Dp, bottom: Dp) {
    val ex = LocalExtra.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 14.dp, end = 14.dp, top = top, bottom = bottom + 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
            // mode quick switch
            Card0(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(t("proxy_mode"), fontSize = 16.sp, color = ex.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val modes = vm.config.modes
                    SegTabs(modes.map { modeName(it) }, modes.indexOf(vm.config.mode).coerceAtLeast(0), { vm.setMode(modes[it]) },
                        dense = net.zash.clashpanel.i18n.I18n.isEn)
                }
            }

            // speed
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigStat(t("upload"), fmtSpeed(vm.traffic.up), Icons.Outlined.ArrowUpward, ex.up, Modifier.weight(1f))
                BigStat(t("download"), fmtSpeed(vm.traffic.down), Icons.Outlined.ArrowDownward, ex.down, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigStat(t("upload_total"), fmtBytes(vm.connTotalUp), Icons.Outlined.CloudUpload, null, Modifier.weight(1f))
                BigStat(t("download_total"), fmtBytes(vm.connTotalDown), Icons.Outlined.CloudDownload, null, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigStat(t("active_conns"), "${vm.activeConns.size}", Icons.Outlined.Link, null, Modifier.weight(1f))
                BigStat(t("memory_usage"), fmtBytes(vm.memory), Icons.Outlined.Memory, null, Modifier.weight(1f))
            }

            // traffic chart
            Card0(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t("live_speed"), fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text(t("peak", fmtSpeed(vm.trafficHistory.maxOfOrNull { maxOf(it.up, it.down) } ?: 0)), fontSize = 12.sp, color = ex.subtle)
                    }
                    Spacer(Modifier.height(10.dp))
                    LineChart(listOf(vm.trafficHistory.map { it.up.toFloat() } to ex.up, vm.trafficHistory.map { it.down.toFloat() } to ex.down), Modifier.fillMaxWidth().height(160.dp))
                    Row(Modifier.padding(top = 8.dp)) { Legend(ex.up, t("upload")); Spacer(Modifier.width(14.dp)); Legend(ex.down, t("download")) }
                }
            }

            // memory chart
            Card0(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t("memory"), fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text(fmtBytes(vm.memory), fontSize = 12.sp, color = ex.subtle)
                    }
                    Spacer(Modifier.height(10.dp))
                    LineChart(listOf(vm.memoryHistory.map { it.toFloat() } to ex.accentUi), Modifier.fillMaxWidth().height(110.dp))
                }
            }

            // top hosts
            val top = remember(vm.activeConns) {
                vm.activeConns.groupBy { it.meta.displayHost }
                    .map { (h, l) -> Triple(h, l.sumOf { it.download + it.upload }, l.size) }
                    .sortedByDescending { it.second }.take(6)
            }
            if (top.isNotEmpty()) {
                Card0(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(t("top_hosts"), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        val max = top.first().second.coerceAtLeast(1)
                        top.forEach { (h, b, n) ->
                            Column(Modifier.padding(vertical = 5.dp)) {
                                Row {
                                    Text(h, Modifier.weight(1f), fontSize = 14.sp, color = ex.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${fmtBytes(b)} · $n", fontSize = 12.sp, color = ex.subtle)
                                }
                                Spacer(Modifier.height(4.dp))
                                Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(ex.chip)) {
                                    Box(Modifier.fillMaxWidth(b.toFloat() / max).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(ex.down))
                                }
                            }
                        }
                    }
                }
            }

            // counts
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigStat(t("proxy_groups"), "${vm.groups.count { it.name != "GLOBAL" }}", Icons.AutoMirrored.Outlined.Send, null, Modifier.weight(1f))
                BigStat(t("rules"), "${vm.rules.size}", Icons.AutoMirrored.Outlined.FormatListBulleted, null, Modifier.weight(1f))
            }
    }
}

@Composable
private fun BigStat(title: String, value: String, icon: ImageVector, color: Color?, modifier: Modifier) {
    val ex = LocalExtra.current
    Card0(modifier) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(16.dp), tint = color ?: ex.subtle)
                Spacer(Modifier.width(6.dp))
                Text(title, fontSize = 13.sp, color = ex.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(6.dp))
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = color ?: MaterialTheme.colorScheme.onSurface, maxLines = 1)
        }
    }
}

@Composable
private fun Legend(c: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(c)); Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 12.sp, color = LocalExtra.current.subtle)
    }
}

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

@Composable
fun OverviewScreen(vm: MainViewModel, contentPadding: PaddingValues) {
    val ex = LocalExtra.current
    Column(Modifier.fillMaxSize().background(ex.bg)) {
        // header
        Row(
            Modifier.fillMaxWidth().background(ex.card).padding(horizontal = 20.dp, vertical = 8.dp).height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("概览", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.width(1.dp).height(20.dp).background(MaterialTheme.colorScheme.outline))
            Spacer(Modifier.width(10.dp))
            val dot = when (vm.connState) { ConnState.Connected -> ex.good; ConnState.Connecting -> ex.warn; ConnState.Failed -> ex.bad; else -> ex.subtle }
            Box(Modifier.size(9.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(8.dp))
            Text(vm.active?.let { "${it.host}:${it.port}" } ?: "未连接", fontSize = 15.sp, color = ex.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (vm.version.isNotBlank()) Tag(vm.version)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = contentPadding.calculateBottomPadding() + 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // mode quick switch
            Card0(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("代理模式", fontSize = 16.sp, modifier = Modifier.weight(1f))
                    val modes = vm.config.modes
                    SegTabs(modes.map { when (it) { "rule" -> "规则"; "global" -> "全局"; "direct" -> "直连"; else -> it } },
                        modes.indexOf(vm.config.mode).coerceAtLeast(0), { vm.setMode(modes[it]) })
                }
            }

            // speed
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigStat("上传", fmtSpeed(vm.traffic.up), Icons.Outlined.ArrowUpward, ex.up, Modifier.weight(1f))
                BigStat("下载", fmtSpeed(vm.traffic.down), Icons.Outlined.ArrowDownward, ex.down, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigStat("上传总量", fmtBytes(vm.connTotalUp), Icons.Outlined.CloudUpload, null, Modifier.weight(1f))
                BigStat("下载总量", fmtBytes(vm.connTotalDown), Icons.Outlined.CloudDownload, null, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigStat("活跃连接", "${vm.activeConns.size}", Icons.Outlined.Link, null, Modifier.weight(1f))
                BigStat("内存占用", fmtBytes(vm.memory), Icons.Outlined.Memory, null, Modifier.weight(1f))
            }

            // traffic chart
            Card0(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("实时速度", fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text("峰值 ${fmtSpeed(vm.trafficHistory.maxOfOrNull { maxOf(it.up, it.down) } ?: 0)}", fontSize = 12.sp, color = ex.subtle)
                    }
                    Spacer(Modifier.height(10.dp))
                    LineChart(listOf(vm.trafficHistory.map { it.up.toFloat() } to ex.up, vm.trafficHistory.map { it.down.toFloat() } to ex.down), Modifier.fillMaxWidth().height(160.dp))
                    Row(Modifier.padding(top = 8.dp)) { Legend(ex.up, "上传"); Spacer(Modifier.width(14.dp)); Legend(ex.down, "下载") }
                }
            }

            // memory chart
            Card0(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("内存", fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text(fmtBytes(vm.memory), fontSize = 12.sp, color = ex.subtle)
                    }
                    Spacer(Modifier.height(10.dp))
                    LineChart(listOf(vm.memoryHistory.map { it.toFloat() } to MaterialTheme.colorScheme.primary), Modifier.fillMaxWidth().height(110.dp))
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
                        Text("流量最多的主机", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        val max = top.first().second.coerceAtLeast(1)
                        top.forEach { (h, b, n) ->
                            Column(Modifier.padding(vertical = 5.dp)) {
                                Row {
                                    Text(h, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                BigStat("代理组", "${vm.groups.count { it.name != "GLOBAL" }}", Icons.Outlined.Language, null, Modifier.weight(1f))
                BigStat("规则", "${vm.rules.size}", Icons.Outlined.Rule, null, Modifier.weight(1f))
            }
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
                Text(title, fontSize = 13.sp, color = ex.subtle)
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

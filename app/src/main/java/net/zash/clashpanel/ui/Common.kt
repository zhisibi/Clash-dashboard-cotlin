package net.zash.clashpanel.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import java.util.Locale

// ---------------- formatting ----------------
fun fmtBytes(b: Long): String {
    if (b < 1024) return "$b B"
    val units = arrayOf("KB", "MB", "GB", "TB", "PB")
    var v = b / 1024.0; var i = 0
    while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
    return String.format(Locale.US, if (v >= 100) "%.0f %s" else if (v >= 10) "%.1f %s" else "%.2f %s", v, units[i])
}
fun fmtSpeed(b: Long) = fmtBytes(b) + "/s"

fun fmtDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return when {
        s < 60 -> "${s}秒"
        s < 3600 -> "${s / 60}分${s % 60}秒"
        s < 86400 -> "${s / 3600}时${(s % 3600) / 60}分"
        else -> "${s / 86400}天${(s % 86400) / 3600}时"
    }
}

fun fmtAgo(ms: Long): String {
    if (ms <= 0) return "-"
    val d = System.currentTimeMillis() - ms
    return when {
        d < 60_000 -> "刚刚"
        d < 3_600_000 -> "${d / 60_000} 分钟前"
        d < 86_400_000 -> "${d / 3_600_000} 小时前"
        else -> "${d / 86_400_000} 天前"
    }
}

fun fmtClock(ms: Long): String = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(java.util.Date(ms))

// ---------------- widgets ----------------
@Composable
fun Card0(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, onLongClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val ex = LocalExtra.current
    val shape = RoundedCornerShape(18.dp)
    var m = modifier.clip(shape).background(ex.card)
    if (onClick != null || onLongClick != null) {
        @OptIn(ExperimentalFoundationApi::class)
        m = m.combinedClickable(onClick = { onClick?.invoke() }, onLongClick = onLongClick)
    }
    Column(m, content = content)
}

@Composable
fun LatencyBadge(delay: Int, low: Int, medium: Int, testing: Boolean = false, onClick: (() -> Unit)? = null) {
    val ex = LocalExtra.current
    val color = when {
        delay <= 0 -> ex.subtle
        delay <= low -> ex.good
        delay <= medium -> ex.warn
        else -> ex.bad
    }
    val m = Modifier.clip(RoundedCornerShape(50)).background(ex.chip)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(horizontal = 10.dp, vertical = 3.dp)
    Box(m, contentAlignment = Alignment.Center) {
        if (testing) {
            val t = rememberInfiniteTransition(label = "spin")
            val a by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "a")
            Icon(Icons.Outlined.Bolt, null, Modifier.size(14.dp).rotate(a), tint = ex.subtle)
        } else {
            Text(if (delay > 0) "$delay" else if (delay == 0) "-" else "超时", color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun SearchBox(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, leading: ImageVector = Icons.Outlined.Search) {
    val ex = LocalExtra.current
    Row(
        modifier.height(44.dp).clip(RoundedCornerShape(14.dp)).background(ex.card)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(leading, null, Modifier.size(18.dp), tint = ex.subtle)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = ex.subtle, fontSize = 15.sp, maxLines = 1)
            BasicTextField(
                value, onChange, singleLine = true,
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Icon(Icons.Outlined.Close, null, Modifier.size(18.dp).clip(CircleShape).clickable { onChange("") }, tint = ex.subtle)
        }
    }
}

@Composable
fun RoundIconButton(icon: ImageVector, active: Boolean = false, tint: Color? = null, onClick: () -> Unit) {
    val ex = LocalExtra.current
    Box(
        Modifier.size(44.dp).clip(CircleShape)
            .background(if (active) MaterialTheme.colorScheme.primary else ex.chip)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = tint ?: if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun SegTabs(items: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val ex = LocalExtra.current
    Row(modifier.clip(RoundedCornerShape(14.dp)).background(ex.chip).padding(4.dp)) {
        items.forEachIndexed { i, s ->
            val sel = i == selected
            Box(
                Modifier.clip(RoundedCornerShape(11.dp))
                    .background(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(i) }.padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(s, color = if (sel) MaterialTheme.colorScheme.onPrimary else ex.subtle, fontSize = 15.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun <T> DropdownChip(label: String, options: List<Pair<T, String>>, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val ex = LocalExtra.current
    Box(modifier) {
        Row(
            Modifier.height(44.dp).clip(RoundedCornerShape(14.dp)).background(ex.card)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                .clickable { open = true }.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.ArrowDropDown, null, tint = ex.subtle)
        }
        DropdownMenu(open, { open = false }) {
            options.forEach { (v, l) -> DropdownMenuItem(text = { Text(l) }, onClick = { open = false; onSelect(v) }) }
        }
    }
}

@Composable
fun ProxyIcon(url: String?, size: Int, modifier: Modifier = Modifier) {
    if (url.isNullOrBlank()) return
    val ctx = LocalContext.current
    val req = remember(url) {
        val b = ImageRequest.Builder(ctx).crossfade(true)
        if (url.startsWith("data:")) {
            val comma = url.indexOf(',')
            val isB64 = url.substring(0, comma.coerceAtLeast(0)).contains("base64")
            val payload = url.substring(comma + 1)
            val bytes = if (isB64) android.util.Base64.decode(payload, android.util.Base64.DEFAULT) else java.net.URLDecoder.decode(payload, "UTF-8").toByteArray()
            b.data(java.nio.ByteBuffer.wrap(bytes))
        } else b.data(url)
        if (url.contains(".svg", true) || url.startsWith("data:image/svg")) b.decoderFactory(SvgDecoder.Factory())
        b.build()
    }
    AsyncImage(req, null, modifier.size(size.dp))
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(start = 6.dp, top = 18.dp, bottom = 8.dp), fontSize = 14.sp, color = LocalExtra.current.subtle, fontWeight = FontWeight.Medium)
}

@Composable
fun EmptyCard(text: String = "暂无数据") {
    Card0(Modifier.fillMaxWidth()) { Text(text, Modifier.padding(18.dp), fontSize = 16.sp) }
}

@Composable
fun Tag(text: String, color: Color? = null) {
    val ex = LocalExtra.current
    Text(
        text, Modifier.clip(RoundedCornerShape(6.dp)).background(ex.chip).padding(horizontal = 6.dp, vertical = 1.dp),
        fontSize = 11.sp, color = color ?: ex.subtle, maxLines = 1,
    )
}

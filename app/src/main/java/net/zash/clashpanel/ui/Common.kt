package net.zash.clashpanel.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Close
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
import net.zash.clashpanel.ConnState
import net.zash.clashpanel.CrashLog
import net.zash.clashpanel.i18n.t
import java.util.Locale

// ---------------- formatting ----------------
fun fmtBytes(b: Long): String {
    if (b < 1024) return "${b.coerceAtLeast(0)} B"
    val units = arrayOf("KB", "MB", "GB", "TB", "PB")
    var v = b / 1024.0; var i = 0
    while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
    return String.format(Locale.US, if (v >= 100) "%.0f %s" else if (v >= 10) "%.1f %s" else "%.2f %s", v, units[i])
}
fun fmtSpeed(b: Long) = fmtBytes(b) + "/s"

fun fmtDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return when {
        s < 60 -> t("dur_s", s)
        s < 3600 -> t("dur_ms", s / 60, s % 60)
        s < 86400 -> t("dur_hm", s / 3600, (s % 3600) / 60)
        else -> t("dur_dh", s / 86400, (s % 86400) / 3600)
    }
}

fun fmtAgo(ms: Long): String {
    if (ms <= 0) return "-"
    val d = System.currentTimeMillis() - ms
    return when {
        d < 60_000 -> t("ago_now")
        d < 3_600_000 -> t("ago_min", d / 60_000)
        d < 86_400_000 -> t("ago_hour", d / 3_600_000)
        else -> t("ago_day", d / 86_400_000)
    }
}

fun fmtClock(ms: Long): String = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(java.util.Date(ms))

fun modeName(m: String) = when (m) { "rule" -> t("mode_rule"); "global" -> t("mode_global"); "direct" -> t("mode_direct"); else -> m }

// ---------------- widgets ----------------
/** Card: frosted glass surface (or plain card color when glass is off). */
@Composable
fun Card0(
    modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, onLongClick: (() -> Unit)? = null,
    kind: GlassKind = GlassKind.Card, radius: Int = 18, content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius.dp)
    var m = modifier.glass(kind, shape).clip(shape)
    if (onClick != null || onLongClick != null) {
        @OptIn(ExperimentalFoundationApi::class)
        m = m.combinedClickable(onClick = { onClick?.invoke() }, onLongClick = onLongClick)
    }
    Column(m, content = content)
}

@Composable
fun LatencyBadge(delay: Int, low: Int, medium: Int, testing: Boolean = false, onClick: (() -> Unit)? = null) {
    val ex = LocalPal.current
    val color = when {
        delay <= 0 -> ex.subtle
        delay <= low -> ex.good
        delay <= medium -> ex.warn
        else -> ex.bad
    }
    val m = Modifier.defaultMinSize(minWidth = 40.dp).height(24.dp).clip(RoundedCornerShape(50)).background(ex.chip)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(horizontal = 10.dp)
    Box(m, contentAlignment = Alignment.Center) {
        if (testing) {
            val tr = rememberInfiniteTransition(label = "spin")
            val a by tr.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "a")
            Icon(Icons.Outlined.Bolt, null, Modifier.size(14.dp).rotate(a), tint = ex.subtle)
        } else {
            Text(if (delay > 0) "$delay" else if (delay == 0) "-" else t("timeout"), color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

@Composable
fun SearchBox(
    value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier,
    leading: ImageVector = Icons.Outlined.Search, dense: Boolean = false,
) {
    val ex = LocalPal.current
    val r = if (dense) 12.dp else 14.dp
    val shape = RoundedCornerShape(r)
    Row(
        modifier.height(if (dense) 34.dp else 44.dp).glass(GlassKind.Plain, shape, tint = ex.card).clip(shape)
            .border(1.dp, ex.outlineVariant, shape).padding(horizontal = if (dense) 10.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(leading, null, Modifier.size(if (dense) 16.dp else 18.dp), tint = ex.subtle)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            val fs = if (dense) 14.sp else 15.sp
            if (value.isEmpty()) Text(placeholder, color = ex.subtle, fontSize = fs, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value, onChange, singleLine = true,
                textStyle = TextStyle(color = ex.text, fontSize = fs),
                cursorBrush = SolidColor(ex.accent), modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Icon(Icons.Outlined.Close, null, Modifier.size(16.dp).clip(CircleShape).clickable { onChange("") }, tint = ex.subtle)
        }
    }
}

@Composable
fun RoundIconButton(icon: ImageVector, active: Boolean = false, tint: Color? = null, dense: Boolean = false, onClick: () -> Unit) {
    val ex = LocalPal.current
    Box(
        Modifier.size(if (dense) 34.dp else 44.dp).clip(CircleShape)
            .background(if (active) ex.primary else ex.chip)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(if (dense) 18.dp else 20.dp), tint = tint ?: if (active) ex.onPrimary else ex.text)
    }
}

@Composable
fun SegTabs(items: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, dense: Boolean = false) {
    val ex = LocalPal.current
    Row(modifier.clip(RoundedCornerShape(if (dense) 12.dp else 14.dp)).background(ex.chip).padding(if (dense) 3.dp else 4.dp)) {
        items.forEachIndexed { i, s ->
            val sel = i == selected
            Box(
                Modifier.clip(RoundedCornerShape(if (dense) 9.dp else 11.dp))
                    .background(if (sel) ex.primary else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = if (dense) 11.dp else 14.dp, vertical = if (dense) 5.dp else 7.dp),
            ) {
                Text(s, color = if (sel) ex.onPrimary else ex.subtle, fontSize = if (dense) 13.sp else 15.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun <T> DropdownChip(label: String, options: List<Pair<T, String>>, onSelect: (T) -> Unit, modifier: Modifier = Modifier, dense: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    val ex = LocalPal.current
    val shape = RoundedCornerShape(if (dense) 12.dp else 14.dp)
    Box(modifier) {
        Row(
            Modifier.height(if (dense) 34.dp else 44.dp).glass(GlassKind.Plain, shape, tint = ex.card).clip(shape)
                .border(1.dp, ex.outlineVariant, shape)
                .clickable { open = true }.padding(start = if (dense) 10.dp else 14.dp, end = if (dense) 6.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = if (dense) 14.sp else 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = ex.text, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(if (dense) 16.dp else 20.dp), tint = ex.subtle)
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
        runCatching {
            if (url.startsWith("data:")) {
                val comma = url.indexOf(',')
                val isB64 = url.substring(0, comma.coerceAtLeast(0)).contains("base64")
                val payload = url.substring(comma + 1)
                val bytes = if (isB64) android.util.Base64.decode(payload, android.util.Base64.DEFAULT) else java.net.URLDecoder.decode(payload, "UTF-8").toByteArray()
                b.data(java.nio.ByteBuffer.wrap(bytes))
            } else b.data(url)
        }
        if (url.contains(".svg", true) || url.startsWith("data:image/svg")) b.decoderFactory(SvgDecoder.Factory())
        b.build()
    }
    AsyncImage(req, null, modifier.size(size.dp))
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(start = 6.dp, top = 18.dp, bottom = 8.dp), fontSize = 14.sp, color = LocalPal.current.subtle, fontWeight = FontWeight.Medium)
}

@Composable
fun EmptyCard(text: String = t("no_data")) {
    Card0(Modifier.fillMaxWidth()) { Text(text, Modifier.padding(18.dp), fontSize = 16.sp, color = LocalPal.current.text) }
}

@Composable
fun Tag(text: String, color: Color? = null, modifier: Modifier = Modifier) {
    val ex = LocalPal.current
    Text(
        text, modifier.clip(RoundedCornerShape(6.dp)).background(ex.chip).padding(horizontal = 6.dp, vertical = 1.dp),
        fontSize = 11.sp, color = color ?: ex.subtle, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
    )
}

/** Page title row: title | status dot + backend address [+ tag] */
@Composable
fun HeaderStatus(title: String, connState: ConnState, address: String, tag: String = "", modifier: Modifier = Modifier) {
    val ex = LocalPal.current
    Row(modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = ex.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 200.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.width(1.dp).height(16.dp).background(ex.outline))
        Spacer(Modifier.width(10.dp))
        val dot = when (connState) { ConnState.Connected -> ex.good; ConnState.Connecting -> ex.warn; ConnState.Failed -> ex.bad; else -> ex.subtle }
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(8.dp))
        Text(address, Modifier.weight(1f), fontSize = 13.sp, color = ex.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (tag.isNotBlank()) Tag(tag)
    }
}

/** Shows a short toast. */
fun toast(ctx: android.content.Context, s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()

/**
 * "Save as" through the system document picker (Storage Access Framework, no storage permission), like the
 * HarmonyOS DocumentViewPicker. Returns a function (fileName, text) that opens the picker and writes the text.
 */
@Composable
fun rememberTextSaver(): (String, String) -> Unit {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val text = pending; pending = null
        if (uri != null && text != null) {
            val r = runCatching { CrashLog.writeUri(ctx, uri, text) }
            toast(ctx, r.fold({ t("saved_file", uri.lastPathSegment?.substringAfterLast('/') ?: "") }, { t("export_failed", it.message ?: "") }))
        }
    }
    return { name, text ->
        pending = text
        runCatching { launcher.launch(name) }.onFailure { pending = null; toast(ctx, t("save_failed")) }
    }
}

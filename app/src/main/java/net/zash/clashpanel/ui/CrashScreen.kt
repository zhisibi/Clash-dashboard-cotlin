package net.zash.clashpanel.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.zash.clashpanel.CrashLog
import java.io.File

/** List of crash logs, shown inside Settings. */
@Composable
fun CrashLogPage() {
    val ctx = LocalContext.current
    val ex = LocalExtra.current
    var files by remember { mutableStateOf(CrashLog.list(ctx)) }
    var open by remember { mutableStateOf<File?>(null) }
    LaunchedEffect(Unit) { CrashLog.markSeen(ctx) }

    Spacer(Modifier.height(16.dp))
    Card0(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("应用闪退时会自动记录错误堆栈。打开一条日志后可以复制、分享或保存到“下载”目录，发给开发者排查。", fontSize = 14.sp, color = ex.subtle)
        }
    }
    Spacer(Modifier.height(12.dp))
    Card0(Modifier.fillMaxWidth()) {
        if (files.isEmpty()) {
            Text("暂无闪退记录", Modifier.padding(20.dp), color = ex.subtle)
        } else {
            files.forEachIndexed { i, f ->
                Row(Modifier.fillMaxWidth().clickable { open = f }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.BugReport, null, tint = ex.bad)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.name.removePrefix("crash-").removeSuffix(".txt").let { "${it.substring(0, 4)}-${it.substring(4, 6)}-${it.substring(6, 8)} ${it.substring(9, 11)}:${it.substring(11, 13)}:${it.substring(13, 15)}" }, fontSize = 16.sp)
                        Text(firstErrorLine(f), fontSize = 12.sp, color = ex.subtle, maxLines = 2)
                    }
                    Icon(Icons.Outlined.ChevronRight, null, tint = ex.subtle)
                }
                if (i < files.size - 1) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
    if (files.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        OutlinedButton({ CrashLog.clear(ctx); files = emptyList(); Toast.makeText(ctx, "已清空", Toast.LENGTH_SHORT).show() }, Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Delete, null); Spacer(Modifier.width(6.dp)); Text("清空闪退日志")
        }
    }
    open?.let { f -> CrashDetailDialog(f) { open = null } }
}

private fun firstErrorLine(f: File): String = runCatching {
    f.readLines().dropWhile { it.isNotBlank() }.firstOrNull { it.isNotBlank() } ?: ""
}.getOrDefault("")

@Composable
fun CrashDetailDialog(f: File, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val text = remember(f) { runCatching { f.readText() }.getOrDefault("") }
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.85f), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp)) {
                Text("闪退日志", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    SelectionContainer {
                        Text(text, Modifier.verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()),
                            fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp, softWrap = false)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ clip.setText(AnnotatedString(text)); Toast.makeText(ctx, "已复制", Toast.LENGTH_SHORT).show() }, Modifier.weight(1f)) { Text("复制", maxLines = 1) }
                    OutlinedButton({ runCatching { CrashLog.share(ctx, text) } }, Modifier.weight(1f)) { Text("分享", maxLines = 1) }
                    OutlinedButton({
                        val r = runCatching { CrashLog.export(ctx, "clashpanel-" + f.name, text) }
                        Toast.makeText(ctx, r.fold({ "已保存到 $it" }, { "保存失败: ${it.message}" }), Toast.LENGTH_LONG).show()
                    }, Modifier.weight(1f)) { Text("保存", maxLines = 1) }
                }
                TextButton(onDismiss, Modifier.align(Alignment.End)) { Text("关闭") }
            }
        }
    }
}

/** Shown once at startup after a crash. */
@Composable
fun CrashPrompt(onOpenLogs: () -> Unit) {
    val ctx = LocalContext.current
    var show by remember { mutableStateOf(CrashLog.hasUnseen(ctx)) }
    var detail by remember { mutableStateOf<File?>(null) }
    if (show) {
        AlertDialog(
            onDismissRequest = { show = false; CrashLog.markSeen(ctx) },
            icon = { Icon(Icons.Outlined.BugReport, null) },
            title = { Text("上次运行发生了闪退") },
            text = { Text("已自动保存错误日志。可以查看后复制或分享给开发者。") },
            confirmButton = { TextButton({ show = false; CrashLog.markSeen(ctx); detail = CrashLog.list(ctx).firstOrNull() }) { Text("查看日志") } },
            dismissButton = { TextButton({ show = false; CrashLog.markSeen(ctx) }) { Text("忽略") } },
        )
    }
    detail?.let { CrashDetailDialog(it) { detail = null } }
}

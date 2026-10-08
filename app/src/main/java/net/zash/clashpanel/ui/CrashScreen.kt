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
import net.zash.clashpanel.i18n.t
import java.io.File

/** List of crash logs, shown inside Settings. */
@Composable
fun CrashLogPage(onSeen: () -> Unit = {}) {
    val ctx = LocalContext.current
    val ex = LocalExtra.current
    var files by remember { mutableStateOf(CrashLog.list(ctx)) }
    var open by remember { mutableStateOf<File?>(null) }
    LaunchedEffect(Unit) { CrashLog.markSeen(ctx); onSeen() }

    Spacer(Modifier.height(16.dp))
    Card0(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(t("crash_intro"), fontSize = 14.sp, color = ex.subtle)
        }
    }
    Spacer(Modifier.height(12.dp))
    Card0(Modifier.fillMaxWidth()) {
        if (files.isEmpty()) {
            Text(t("no_crash"), Modifier.padding(20.dp), color = ex.subtle)
        } else {
            files.forEachIndexed { i, f ->
                Row(Modifier.fillMaxWidth().clickable { open = f }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.BugReport, null, tint = ex.bad)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.name.removePrefix("crash-").removeSuffix(".txt").let { runCatching { "${it.substring(0, 4)}-${it.substring(4, 6)}-${it.substring(6, 8)} ${it.substring(9, 11)}:${it.substring(11, 13)}:${it.substring(13, 15)}" }.getOrDefault(it) }, fontSize = 16.sp, color = ex.text)
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
        OutlinedButton({ CrashLog.clear(ctx); files = emptyList(); Toast.makeText(ctx, t("cleared"), Toast.LENGTH_SHORT).show() }, Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Delete, null); Spacer(Modifier.width(6.dp)); Text(t("clear_crash"))
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
    val save = rememberTextSaver()
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.85f), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp)) {
                Text(t("crash_log"), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    SelectionContainer {
                        Text(text, Modifier.verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()),
                            fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp, softWrap = false)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ clip.setText(AnnotatedString(text)); Toast.makeText(ctx, t("copied"), Toast.LENGTH_SHORT).show() }, Modifier.weight(1f)) { Text(t("copy"), maxLines = 1) }
                    OutlinedButton({ runCatching { CrashLog.share(ctx, text) }.onFailure { Toast.makeText(ctx, t("share_failed"), Toast.LENGTH_SHORT).show() } }, Modifier.weight(1f)) { Text(t("share"), maxLines = 1) }
                    OutlinedButton({ save("mimi-panel-" + f.name, text) }, Modifier.weight(1f)) { Text(t("save"), maxLines = 1) }
                }
                TextButton(onDismiss, Modifier.align(Alignment.End)) { Text(t("close_btn")) }
            }
        }
    }
}

/** Shown once at startup after a crash. */
@Composable
fun CrashPrompt(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var show by remember { mutableStateOf(CrashLog.hasUnseen(ctx)) }
    var detail by remember { mutableStateOf<File?>(null) }
    LaunchedEffect(show, detail) { if (!show && detail == null) onDone() }
    if (show) {
        AlertDialog(
            onDismissRequest = { show = false; CrashLog.markSeen(ctx) },
            icon = { Icon(Icons.Outlined.BugReport, null) },
            title = { Text(t("crash_prompt_title")) },
            text = { Text(t("crash_prompt_msg")) },
            confirmButton = { TextButton({ show = false; CrashLog.markSeen(ctx); detail = CrashLog.list(ctx).firstOrNull() }) { Text(t("view_log")) } },
            dismissButton = { TextButton({ show = false; CrashLog.markSeen(ctx) }) { Text(t("ignore")) } },
        )
    }
    detail?.let { CrashDetailDialog(it) { detail = null } }
}

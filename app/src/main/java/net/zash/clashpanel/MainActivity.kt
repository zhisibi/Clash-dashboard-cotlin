package net.zash.clashpanel

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.zash.clashpanel.data.Backend
import net.zash.clashpanel.ui.*

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dark = when (vm.theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                )
            }
            LaunchedEffect(Unit) {
                vm.toastFlow.collect { Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show() }
            }
            ClashTheme(vm.theme, vm.wallpaper != null, vm.cardAlpha) { App(vm) }
        }
    }
}

private data class Tab(val title: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("概览", Icons.Outlined.Dashboard),
    Tab("代理", Icons.Outlined.Language),
    Tab("连接", Icons.AutoMirrored.Outlined.CompareArrows),
    Tab("日志", Icons.Outlined.Description),
    Tab("规则", Icons.AutoMirrored.Outlined.Rule),
    Tab("设置", Icons.Outlined.Settings),
)

@Composable
fun App(vm: MainViewModel) {
    val ex = LocalExtra.current
    if (vm.backends.isEmpty()) {
        SetupScreen(vm); return
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        vm.wallpaper?.let { wp ->
            androidx.compose.foundation.Image(
                wp, null,
                Modifier.fillMaxSize().let { m -> if (vm.wallpaperBlur > 0.5f && android.os.Build.VERSION.SDK_INT >= 31) m.blur(vm.wallpaperBlur.dp) else m },
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
            if (vm.wallpaperDim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = vm.wallpaperDim)))
        }
        CrashPrompt {}
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            if (vm.connState == ConnState.Failed && tab != 5) {
                Row(
                    Modifier.fillMaxWidth().background(ex.bad.copy(alpha = 0.12f)).clickable { tab = 5 }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.ErrorOutline, null, tint = ex.bad, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("后端连接失败：${vm.connError}", Modifier.weight(1f), color = ex.bad, fontSize = 13.sp)
                    TextButton({ vm.retry() }) { Text("重试") }
                }
            }
            val bottomPad = PaddingValues(bottom = 96.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> OverviewScreen(vm, bottomPad)
                    1 -> ProxiesScreen(vm, bottomPad)
                    2 -> ConnectionsScreen(vm, bottomPad)
                    3 -> LogsScreen(vm, bottomPad)
                    4 -> RulesScreen(vm, bottomPad)
                    else -> SettingsScreen(vm, bottomPad)
                }
            }
        }
        BottomBar(tab, { tab = it }, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    val ex = LocalExtra.current
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(
            Modifier.fillMaxWidth().shadow(10.dp, RoundedCornerShape(40.dp), spotColor = Color.Black.copy(alpha = 0.25f))
                .clip(RoundedCornerShape(40.dp)).background(ex.card.copy(alpha = 0.97f)).padding(6.dp),
        ) {
            tabs.forEachIndexed { i, t ->
                val sel = i == selected
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(34.dp))
                        .background(if (sel) ex.chip else Color.Transparent)
                        .clickable { onSelect(i) }.padding(vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(t.icon, t.title, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(2.dp))
                    Text(t.title, fontSize = 11.sp, maxLines = 1, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun SetupScreen(vm: MainViewModel) {
    val ex = LocalExtra.current
    Box(Modifier.fillMaxSize().background(ex.bg).systemBarsPadding().imePadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Spacer(Modifier.height(40.dp))
            Icon(Icons.Outlined.Public, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("连接 Clash 后端", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("填写 external-controller 的地址与密钥。本机内核一般是 127.0.0.1:9090。", fontSize = 14.sp, color = ex.subtle)
            Spacer(Modifier.height(24.dp))
            Card0(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) { BackendForm(vm, Backend(vm.newBackendId())) }
            }
        }
    }
}

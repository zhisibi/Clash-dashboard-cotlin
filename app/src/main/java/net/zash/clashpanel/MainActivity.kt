package net.zash.clashpanel

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import net.zash.clashpanel.data.Backend
import net.zash.clashpanel.i18n.I18n
import net.zash.clashpanel.i18n.t
import net.zash.clashpanel.ui.*
import kotlin.math.max
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm.hashCode() // creates the view model (loads the language preference) before the system language check
        I18n.onSystemChanged()
        setContent {
            val dark = when (vm.theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
            LaunchedEffect(dark) {
                val tr = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(tr) else SystemBarStyle.light(tr, tr),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(tr) else SystemBarStyle.light(tr, tr),
                )
                // no gray scrim behind the gesture bar: the floating bar and pages draw edge to edge
                if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
            }
            LaunchedEffect(Unit) {
                vm.toastFlow.collect { Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show() }
            }
            AppRoot(vm, dark) { vm.declinePrivacy(); finishAndRemoveTask() }
        }
    }
}

private data class Tab(val title: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("tab_overview", Icons.Outlined.Home),
    Tab("tab_proxies", Icons.AutoMirrored.Outlined.Send),
    Tab("tab_conns", Icons.AutoMirrored.Outlined.CompareArrows),
    Tab("tab_logs", Icons.Outlined.Description),
    Tab("tab_rules", Icons.AutoMirrored.Outlined.FormatListBulleted),
    Tab("tab_settings", Icons.Outlined.Settings),
)
private val PAGE_IDS = listOf("overview", "proxies", "connections", "logs", "rules", null)

/** Height of the expanded bottom bar / diameter of the collapsed round button */
private val BAR_H = 64.dp
private val DOT = 56.dp

@Composable
fun AppRoot(vm: MainViewModel, dark: Boolean, onExit: () -> Unit) {
    val main = vm.privacyAgreed && vm.backends.isNotEmpty()
    val wp = if (main) vm.wallpaper else null
    val pal = buildPalette(dark, vm.accent, vm.glassOn, vm.glassAlpha, wp != null, vm.cardAlpha)
    val cfg = GlassCfg(vm.glassOn, vm.glassStyle, vm.glassBlur, vm.glassRows, vm.lightOn, vm.lightIntensity, vm.lightGlow, vm.lightDeg)
    TiltSensor(vm.lightOn && vm.lightTilt, vm.lightDeg) { vm.lightDeg = it }
    val bgBd = rememberBackdrop()
    val contentBd = rememberBackdrop()
    MimiTheme(pal) {
        CompositionLocalProvider(LocalGlass provides cfg, LocalBgBackdrop provides bgBd, LocalContentBackdrop provides contentBd) {
            // Keyed by the UI language: switching rebuilds the tree (tab / settings sub-page live in the view model)
            key(I18n.lang) {
                val ms = if (main) rememberMainState(vm) else null
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().background(if (wp != null) Color.Black else if (dark) Color(0xFF111316) else Color(0xFFF4F4F6)))
                    Box(Modifier.fillMaxSize().captureBackdrop(contentBd)) {
                        Box(Modifier.fillMaxSize().captureBackdrop(bgBd)) {
                            if (vm.glassOn && wp == null) GlassBackdrop(vm.lightOn, vm.lightDeg)
                            if (wp != null) {
                                Image(
                                    wp, null,
                                    Modifier.fillMaxSize().let { m -> if (vm.wallpaperBlur > 0.5f && Build.VERSION.SDK_INT >= 31) m.blur(vm.wallpaperBlur.dp) else m },
                                    contentScale = ContentScale.Crop,
                                )
                                if (vm.wallpaperDim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = vm.wallpaperDim)))
                            }
                        }
                        when {
                            !vm.privacyAgreed -> ConsentScreen(vm, onExit)
                            vm.backends.isEmpty() -> SetupScreen(vm)
                            else -> MainPages(vm, ms!!)
                        }
                    }
                    if (main) MainChrome(vm, ms!!)
                    if (vm.legalDoc.isNotEmpty()) {
                        BackHandler { vm.legalDoc = "" }
                        LegalView(vm.legalDoc) { vm.legalDoc = "" }
                    }
                    if (vm.privacyAgreed && vm.crashPrompt) CrashPrompt { vm.crashPrompt = false }
                }
            }
        }
    }
}

/** State shared by the pages (inside the content backdrop) and the chrome drawn over them (headers, bar). */
private class MainState(val pager: PagerState) {
    val headerH = mutableStateMapOf<Int, Dp>()
    var sweepKey by mutableIntStateOf(0)
}

@Composable
private fun rememberMainState(vm: MainViewModel): MainState {
    val pager = rememberPagerState(initialPage = vm.tab.coerceIn(0, 5)) { 6 }
    val ms = remember(pager) { MainState(pager) }
    // pager -> view model (tap or swipe); the bar expands again after a page change
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { i ->
            if (vm.tab != i) { vm.tab = i; ms.sweepKey++ }
            vm.expandNav()
        }
    }
    // view model -> pager (e.g. the failure banner opens Settings)
    LaunchedEffect(vm.tab) {
        if (pager.settledPage != vm.tab && !pager.isScrollInProgress) pager.animateScrollToPage(vm.tab)
    }
    return ms
}

@Composable
private fun navBottom(): Dp {
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return if (navInset > 0.dp) max(8f, navInset.value - 6f).dp else 10.dp
}

/** The six pages in a pager (left/right swipe), each with pull to refresh and room for the header and the bar. */
@Composable
private fun MainPages(vm: MainViewModel, ms: MainState) {
    val nb = navBottom()
    val failed = vm.connState == ConnState.Failed
    val g1 = headerGuess(1); val g2 = headerGuess(2); val g3 = headerGuess(3)
    fun guess(i: Int) = when (i) { 1, 3, 4 -> g2; 2 -> g3; else -> g1 }
    fun bottomPad(i: Int) = nb + BAR_H + 16.dp + if (failed && i != 5) 52.dp else 0.dp
    HorizontalPager(ms.pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1, key = { it }) { i ->
        val top = ms.headerH[i] ?: guess(i)
        PageFrame(vm, PAGE_IDS[i], top) {
            val contentTop = top + 14.dp
            when (i) {
                0 -> OverviewBody(vm, contentTop, bottomPad(0))
                1 -> ProxiesBody(vm, contentTop, bottomPad(1))
                2 -> ConnectionsBody(vm, contentTop, bottomPad(2))
                3 -> LogsBody(vm, contentTop, bottomPad(3))
                4 -> RulesBody(vm, contentTop, bottomPad(4))
                else -> SettingsBody(vm, contentTop, bottomPad(5))
            }
        }
    }
}

/** Frosted headers (sliding with the pages), failure banner and floating bar, drawn over the captured pages. */
@Composable
private fun MainChrome(vm: MainViewModel, ms: MainState) {
    val pager = ms.pager
    val scope = rememberCoroutineScope()
    BackHandler(vm.tab == 5 && vm.settingsPage.isNotEmpty() && vm.legalDoc.isEmpty()) { vm.settingsPage = ""; vm.expandNav() }
    Box(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxWidth().clickable(remember { MutableInteractionSource() }, null) {}) {
            val w = constraints.maxWidth
            val cur = pager.currentPage
            for (i in (cur - 1).coerceAtLeast(0)..(cur + 1).coerceAtMost(5)) {
                key(i) {
                    Box(Modifier.fillMaxWidth().offset { IntOffset(((i - pager.currentPage - pager.currentPageOffsetFraction) * w).roundToInt(), 0) }) {
                        PageHeader(vm, i) { ms.headerH[i] = it }
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = navBottom())) {
            if (vm.connState == ConnState.Failed && vm.tab != 5) FailBanner(vm)
            BottomBar(vm, pager, ms.sweepKey) { i -> scope.launch { if (i != pager.currentPage) pager.animateScrollToPage(i) } }
        }
    }
}

@Composable
private fun PageHeader(vm: MainViewModel, i: Int, onHeight: (Dp) -> Unit) {
    when (i) {
        0 -> GlassHeader(hPad = 20.dp, onHeight = onHeight) {
            HeaderStatus(t("overview"), vm.connState, vm.active?.let { "${it.host}:${it.port}" } ?: t("not_connected"), vm.version)
        }
        1 -> GlassHeader(onHeight = onHeight) { ProxiesHeader(vm) }
        2 -> GlassHeader(onHeight = onHeight) { ConnectionsHeader(vm) }
        3 -> GlassHeader(onHeight = onHeight) { LogsHeader(vm) }
        4 -> GlassHeader(onHeight = onHeight) { RulesHeader(vm) }
        else -> GlassHeader(onHeight = onHeight) { SettingsHeader(vm) }
    }
}

@Composable
private fun FailBanner(vm: MainViewModel) {
    val p = LocalPal.current
    Row(
        Modifier.padding(bottom = 8.dp).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(22.dp))
            .background((if (p.dark) Color(0xFF3A1F22) else Color(0xFFFDECEC)).copy(alpha = 0.94f))
            .clickable { vm.settingsPage = "backend"; vm.tab = 5; vm.expandNav() }
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.ErrorOutline, null, tint = p.bad, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(t("backend_failed_banner", vm.connError ?: ""), Modifier.weight(1f), color = p.bad, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        TextButton({ vm.retry() }) { Text(t("retry"), color = p.text, maxLines = 1) }
    }
}

/**
 * Floating frosted bottom bar; auto-collapses to a round glass button at the bottom left (current page icon)
 * when scrolling down; tap the button to expand. Light sweep runs once across the bar on page change.
 */
@Composable
private fun BottomBar(vm: MainViewModel, pager: PagerState, sweepKey: Int, onSelect: (Int) -> Unit) {
    val p = LocalPal.current
    val collapsed = vm.autoHideNav && vm.navCollapsed
    val sel = pager.targetPage
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val full = maxWidth
        val spec = tween<Dp>(380, easing = FastOutSlowInEasing)
        val w by animateDpAsState(if (collapsed) DOT else full, spec, label = "w")
        val h by animateDpAsState(if (collapsed) DOT else BAR_H, spec, label = "h")
        val r by animateDpAsState(if (collapsed) DOT / 2 else 40.dp, spec, label = "r")
        val sweep = remember { Animatable(-1f) }
        var sweepOn by remember { mutableStateOf(false) }
        LaunchedEffect(sweepKey) {
            if (sweepKey > 0 && vm.lightOn && vm.lightSweep) {
                sweepOn = true; sweep.snapTo(-1f)
                sweep.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
                sweepOn = false
            }
        }
        val shape = RoundedCornerShape(r)
        Box(
            Modifier.width(w).height(h).glass(GlassKind.Bar, shape).clip(shape)
                .clickable(remember { MutableInteractionSource() }, null) { if (collapsed) vm.expandNav() },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(collapsed, transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(initialScale = 0.6f)) togetherWith (fadeOut(tween(150)) + scaleOut(targetScale = 0.6f))
            }, label = "bar") { c ->
                if (c) {
                    Box(Modifier.size(DOT), contentAlignment = Alignment.Center) {
                        Icon(TABS[sel.coerceIn(0, 5)].icon, null, Modifier.size(24.dp), tint = p.accentUi)
                    }
                } else {
                    Row(Modifier.requiredWidth(full).fillMaxHeight().padding(6.dp)) {
                        TABS.forEachIndexed { i, tab ->
                            val on = i == sel
                            Column(
                                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(34.dp))
                                    .background(if (on) p.accentSoft else Color.Transparent)
                                    .clickable { onSelect(i) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(tab.icon, t(tab.title), Modifier.size(22.dp), tint = if (on) p.accentUi else p.text)
                                Spacer(Modifier.height(2.dp))
                                AutoSizeLabel(t(tab.title), on, if (on) p.accent else p.text)
                            }
                        }
                    }
                }
            }
            if (sweepOn) {
                Box(Modifier.matchParentSize().drawWithContent {
                    val bw = size.width * 0.45f
                    val x = sweep.value * size.width * 0.75f + (size.width - bw) / 2
                    val a = (if (p.dark) 0.22f else 0.55f) * vm.lightIntensity
                    drawRect(
                        Brush.horizontalGradient(
                            0f to Color.Transparent, 0.5f to Color.White.copy(alpha = a), 1f to Color.Transparent,
                            startX = x, endX = x + bw,
                        ),
                        topLeft = Offset(x, 0f), size = androidx.compose.ui.geometry.Size(bw, size.height),
                    )
                })
            }
        }
    }
}

/** Tab label: 11 sp, shrinks down to 8 sp when it doesn't fit (English labels). */
@Composable
private fun AutoSizeLabel(text: String, bold: Boolean, color: Color) {
    var size by remember(text) { mutableFloatStateOf(11f) }
    Text(
        text, color = color, fontSize = size.sp, maxLines = 1, softWrap = false,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        overflow = if (size <= 8f) TextOverflow.Ellipsis else TextOverflow.Clip,
        onTextLayout = { if (it.hasVisualOverflow && size > 8f) size -= 0.5f },
    )
}

/** First launch: no network requests before consent; declining exits the app. */
@Composable
private fun ConsentScreen(vm: MainViewModel, onExit: () -> Unit) {
    val p = LocalPal.current
    val sys = WindowInsets.systemBars.asPaddingValues()
    Column(Modifier.fillMaxSize().background(p.bg)) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = sys.calculateTopPadding() + 40.dp, bottom = 16.dp),
        ) {
            AppIcon(72.dp)
            Text(t("consent_welcome"), Modifier.padding(top = 16.dp), fontSize = 24.sp, fontWeight = FontWeight.Medium, color = p.text)
            Text(t("consent_intro"), Modifier.padding(top = 10.dp), fontSize = 14.sp, lineHeight = 22.sp, color = p.subtle)
            Column(Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.chip).padding(16.dp)) {
                Bullet(t("consent_p1")); Bullet(t("consent_p2")); Bullet(t("consent_p3")); Bullet(t("consent_p4"))
            }
            val text = buildAnnotatedString {
                append(t("consent_read"))
                pushStringAnnotation("doc", "privacy"); withStyle(SpanStyle(color = p.accent)) { append(t("privacy_policy_q")) }; pop()
                append(t("consent_and"))
                pushStringAnnotation("doc", "agreement"); withStyle(SpanStyle(color = p.accent)) { append(t("user_agreement_q")) }; pop()
                append(t("consent_tail"))
            }
            @Suppress("DEPRECATION")
            ClickableText(text, Modifier.padding(top = 16.dp), style = TextStyle(fontSize = 14.sp, lineHeight = 22.sp, color = p.text)) { off ->
                text.getStringAnnotations("doc", off, off).firstOrNull()?.let { vm.legalDoc = it.item }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = sys.calculateBottomPadding() + 16.dp)) {
            Button(onExit, Modifier.weight(1f).height(44.dp), colors = ButtonDefaults.buttonColors(containerColor = p.chip, contentColor = p.text)) { Text(t("disagree"), maxLines = 1) }
            Spacer(Modifier.width(12.dp))
            Button({ vm.agreePrivacy() }, Modifier.weight(1f).height(44.dp), colors = primaryButtonColors()) { Text(t("agree"), maxLines = 1) }
        }
    }
}

/** The launcher icon (cat head) drawn from its two adaptive layers. */
@Composable
fun AppIcon(size: Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size / 4))) {
        Image(painterResource(R.drawable.ic_launcher_bg), null, Modifier.requiredSize(size * 1.5f), contentScale = ContentScale.Crop)
        Image(painterResource(R.drawable.ic_launcher_fg), null, Modifier.requiredSize(size * 1.5f).align(Alignment.Center))
    }
}

@Composable
private fun SetupScreen(vm: MainViewModel) {
    val p = LocalPal.current
    Box(Modifier.fillMaxSize().background(p.bg).systemBarsPadding().imePadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Spacer(Modifier.height(40.dp))
            Icon(Icons.AutoMirrored.Outlined.Send, null, Modifier.size(56.dp), tint = p.accentUi)
            Spacer(Modifier.height(16.dp))
            Text(t("setup_title"), fontSize = 26.sp, fontWeight = FontWeight.Medium, color = p.text)
            Spacer(Modifier.height(6.dp))
            Text(t("setup_desc"), fontSize = 14.sp, color = p.subtle)
            Spacer(Modifier.height(24.dp))
            Card0(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) { BackendForm(vm, Backend(vm.newBackendId())) }
            }
        }
    }
}

/** Full privacy policy / user agreement page shown over the current screen. */
@Composable
private fun LegalView(doc: String, onClose: () -> Unit) {
    val p = LocalPal.current
    val sys = WindowInsets.systemBars.asPaddingValues()
    val lines = if (doc == "agreement") I18n.agreement() else I18n.privacy()
    Column(Modifier.fillMaxSize().background(if (p.dark) Color(0xFF111316) else Color(0xFFF4F4F6)).clickable(remember { MutableInteractionSource() }, null) {}) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 14.dp, top = sys.calculateTopPadding() + 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = p.text) }
            Text(if (doc == "agreement") t("user_agreement") else t("privacy_policy"), fontSize = 18.sp, fontWeight = FontWeight.Medium, color = p.text)
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = sys.calculateBottomPadding() + 24.dp)) {
            lines.forEach { l ->
                when {
                    l.startsWith("# ") -> Text(l.substring(2), Modifier.padding(bottom = 6.dp), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = p.text)
                    l.startsWith("## ") -> Text(l.substring(3), Modifier.padding(top = 10.dp, bottom = 4.dp), fontSize = 17.sp, fontWeight = FontWeight.Medium, color = p.text)
                    l.startsWith("- ") -> Row(Modifier.padding(vertical = 2.dp)) {
                        Text("•", Modifier.width(14.dp), fontSize = 14.sp, color = p.subtle)
                        Text(l.substring(2), fontSize = 14.sp, lineHeight = 22.sp, color = p.text)
                    }
                    l.isEmpty() -> Spacer(Modifier.height(6.dp))
                    else -> Text(l, fontSize = 14.sp, lineHeight = 22.sp, color = p.text)
                }
            }
        }
    }
}
